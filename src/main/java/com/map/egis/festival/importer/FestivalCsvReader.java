package com.map.egis.festival.importer;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.List;

@Component
public class FestivalCsvReader {
    public List<List<String>> read(byte[] bytes, String encoding) {
        if (bytes == null || bytes.length == 0) {
            throw new InvalidFestivalCsvException("CSV 파일이 비어 있습니다.");
        }
        Charset charset = switch (encoding == null ? "" : encoding.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "MS949" -> Charset.forName("MS949");
            case "UTF-8" -> java.nio.charset.StandardCharsets.UTF_8;
            default -> throw new InvalidFestivalCsvException("인코딩은 MS949 또는 UTF-8만 지원합니다.");
        };
        try {
            String text = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            return parse(text);
        } catch (CharacterCodingException error) {
            throw new InvalidFestivalCsvException("선택한 인코딩으로 파일을 해석할 수 없습니다.", error);
        }
    }

    static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean quoteClosed = false;

        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (quoted) {
                if (current == '"') {
                    if (index + 1 < text.length() && text.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                        quoteClosed = true;
                    }
                } else {
                    field.append(current);
                }
                continue;
            }
            if (quoteClosed && current != ',' && current != '\r' && current != '\n') {
                throw new InvalidFestivalCsvException("CSV 따옴표 뒤의 값 형식이 올바르지 않습니다.");
            }
            if (current == '"' && field.isEmpty() && !quoteClosed) {
                quoted = true;
            } else if (current == ',') {
                row.add(field.toString());
                field.setLength(0);
                quoteClosed = false;
            } else if (current == '\r' || current == '\n') {
                if (current == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') index++;
                row.add(field.toString());
                addNonBlankRow(rows, row);
                row = new ArrayList<>();
                field.setLength(0);
                quoteClosed = false;
            } else {
                if (current == '"') throw new InvalidFestivalCsvException("CSV 따옴표가 올바르지 않습니다.");
                field.append(current);
            }
        }
        if (quoted) throw new InvalidFestivalCsvException("CSV 따옴표가 닫히지 않았습니다.");
        if (!row.isEmpty() || !field.isEmpty() || quoteClosed) {
            row.add(field.toString());
            addNonBlankRow(rows, row);
        }
        return rows;
    }

    private static void addNonBlankRow(List<List<String>> rows, List<String> row) {
        if (row.stream().anyMatch(value -> !value.isBlank())) rows.add(List.copyOf(row));
    }
}
