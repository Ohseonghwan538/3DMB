package com.map.egis.festival.importer;

import com.map.egis.festival.mapper.FestivalMapper;
import com.map.egis.festival.mapper.ImportHistoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class FestivalImportTransactionService {
    private static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    private static final Map<String, Integer> FIELD_LIMITS = Map.of(
            "축제명", 200, "개최장소", 200, "소재지도로명주소", 300,
            "소재지지번주소", 300, "주최기관명", 200, "주관기관명", 200);
    private static final List<String> REQUIRED_HEADERS = List.of("축제명", "축제시작일자", "축제종료일자", "위도", "경도");
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"));

    private final FestivalCsvReader csvReader;
    private final FestivalMapper festivalMapper;
    private final ImportHistoryMapper importHistoryMapper;
    private final TypeClassifier typeClassifier;

    public FestivalImportTransactionService(FestivalCsvReader csvReader, FestivalMapper festivalMapper,
                                            ImportHistoryMapper importHistoryMapper, TypeClassifier typeClassifier) {
        this.csvReader = csvReader;
        this.festivalMapper = festivalMapper;
        this.importHistoryMapper = importHistoryMapper;
        this.typeClassifier = typeClassifier;
    }

    @Transactional
    public FestivalImportService.ImportResult importCsv(String originalFileName, byte[] bytes, String encoding) {
        String fileName = safeFileName(originalFileName);
        validateUpload(fileName, bytes);
        List<List<String>> rows = csvReader.read(bytes, encoding);
        if (rows.isEmpty()) throw new InvalidFestivalCsvException("CSV 헤더가 없습니다.");
        Map<String, Integer> columns = indexHeaders(rows.get(0));
        List<String> missing = REQUIRED_HEADERS.stream().filter(header -> !columns.containsKey(header)).toList();
        if (!missing.isEmpty()) {
            throw new InvalidFestivalCsvException("필수 헤더가 없습니다: " + String.join(", ", missing));
        }

        int total = rows.size() - 1;
        int succeeded = 0;
        int skipped = 0;
        List<FestivalImportService.SkippedRow> skippedDetails = new ArrayList<>();
        for (int index = 1; index < rows.size(); index++) {
            int rowNumber = index + 1;
            try {
                FestivalRow festival = validateRow(rows.get(index), columns);
                festivalMapper.upsert(festival.name(), festival.venue(), festival.startDate(),
                        festival.endDate(), festival.content(), festival.hostOrg(), festival.mngOrg(),
                        festival.roadAddr(), festival.lotAddr(), festival.sido(), festival.sigungu(),
                        festival.latitude(), festival.longitude(), festival.typeCode());
                succeeded++;
            } catch (InvalidFestivalCsvException invalidRow) {
                skipped++;
                log.warn("Skipping invalid festival CSV row {}: {}", rowNumber, invalidRow.getMessage());
                if (skippedDetails.size() < 100) {
                    skippedDetails.add(new FestivalImportService.SkippedRow(rowNumber, invalidRow.getMessage()));
                }
            }
        }
        importHistoryMapper.insert(fileName, total, succeeded, skipped);
        log.info("Festival CSV import completed: file={}, totalRows={}, successRows={}, skippedRows={}",
                fileName, total, succeeded, skipped);
        return new FestivalImportService.ImportResult(fileName, total, succeeded, skipped, List.copyOf(skippedDetails));
    }

    private void validateUpload(String fileName, byte[] bytes) {
        if (!fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".csv")) {
            throw new InvalidFestivalCsvException("CSV 파일(.csv)만 업로드할 수 있습니다.");
        }
        if (bytes == null || bytes.length == 0) throw new InvalidFestivalCsvException("CSV 파일이 비어 있습니다.");
        if (bytes.length > MAX_FILE_BYTES) throw new InvalidFestivalCsvException("파일 크기는 5MB를 초과할 수 없습니다.");
    }

    private Map<String, Integer> indexHeaders(List<String> headers) {
        Map<String, Integer> columns = new HashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            String name = headers.get(index).trim();
            if (!name.isEmpty() && columns.putIfAbsent(name, index) != null) {
                throw new InvalidFestivalCsvException("CSV 헤더가 중복되었습니다: " + name);
            }
        }
        return columns;
    }

    private FestivalRow validateRow(List<String> row, Map<String, Integer> columns) {
        String name = value(row, columns, "축제명");
        if (name.isBlank()) throw new InvalidFestivalCsvException("축제명이 비어 있습니다.");
        for (Map.Entry<String, Integer> field : FIELD_LIMITS.entrySet()) {
            if (value(row, columns, field.getKey()).length() > field.getValue()) {
                throw new InvalidFestivalCsvException(field.getKey() + " 길이가 허용 범위를 초과합니다.");
            }
        }
        LocalDate startDate = parseDate(value(row, columns, "축제시작일자"), "축제시작일자");
        LocalDate endDate = parseDate(value(row, columns, "축제종료일자"), "축제종료일자");
        if (endDate.isBefore(startDate)) throw new InvalidFestivalCsvException("종료일이 시작일보다 빠릅니다.");
        BigDecimal latitude = parseCoordinate(value(row, columns, "위도"), "위도", "33", "39");
        BigDecimal longitude = parseCoordinate(value(row, columns, "경도"), "경도", "124", "132");

        String venue = value(row, columns, "개최장소").trim();
        String roadAddr = nullable(value(row, columns, "소재지도로명주소"));
        String lotAddr = nullable(value(row, columns, "소재지지번주소"));
        String content = nullable(value(row, columns, "축제내용"));
        String sidoAddress = roadAddr == null ? lotAddr : roadAddr;
        String[] addressParts = sidoAddress == null ? new String[0] : sidoAddress.trim().split("\\s+", 3);
        String sido = addressParts.length > 0 ? addressParts[0] : null;
        String sigungu = addressParts.length > 1 ? addressParts[1] : null;

        return new FestivalRow(name.trim(), venue, startDate, endDate, content,
                nullable(value(row, columns, "주최기관명")),
                nullable(value(row, columns, "주관기관명")),
                roadAddr, lotAddr, sido, sigungu, latitude, longitude,
                typeClassifier.classify(name, content));
    }

    private LocalDate parseDate(String value, String label) {
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value.trim(), formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported standard-data date format.
            }
        }
        throw new InvalidFestivalCsvException(label + " 날짜 형식이 올바르지 않습니다.");
    }

    private BigDecimal parseCoordinate(String value, String label, String minimum, String maximum) {
        try {
            BigDecimal number = new BigDecimal(value.trim());
            if (number.compareTo(new BigDecimal(minimum)) < 0 || number.compareTo(new BigDecimal(maximum)) > 0) {
                throw new InvalidFestivalCsvException(label + " 범위 오류");
            }
            return number;
        } catch (NumberFormatException | NullPointerException invalidNumber) {
            throw new InvalidFestivalCsvException(label + " 값이 숫자가 아닙니다.");
        }
    }

    private String value(List<String> row, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        return index == null || index >= row.size() ? "" : row.get(index);
    }

    private String nullable(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeFileName(String fileName) {
        if (fileName == null) return "";
        String basename = fileName.replace('\\', '/');
        basename = basename.substring(basename.lastIndexOf('/') + 1).trim();
        if (basename.length() > 300) basename = basename.substring(basename.length() - 300);
        return basename;
    }

    private record FestivalRow(String name, String venue, LocalDate startDate, LocalDate endDate,
                               String content, String hostOrg, String mngOrg, String roadAddr,
                               String lotAddr, String sido, String sigungu, BigDecimal latitude,
                               BigDecimal longitude, String typeCode) { }
}
