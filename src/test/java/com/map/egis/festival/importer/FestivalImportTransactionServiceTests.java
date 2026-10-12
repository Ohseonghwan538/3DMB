package com.map.egis.festival.importer;

import com.map.egis.festival.mapper.FestivalMapper;
import com.map.egis.festival.mapper.ImportHistoryMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FestivalImportTransactionServiceTests {
    @Test
    void importsValidRowsAndReportsInvalidCoordinates() {
        FestivalMapper festivals = mock(FestivalMapper.class);
        ImportHistoryMapper history = mock(ImportHistoryMapper.class);
        FestivalImportService service = new FestivalImportService(
                new FestivalImportTransactionService(
                        new FestivalCsvReader(), festivals, history, new TypeClassifier()));
        String csv = """
                축제명,축제시작일자,축제종료일자,위도,경도,개최장소,소재지도로명주소,축제내용
                서울 재즈 축제,2026-06-01,2026-06-02,37.5,127.0,서울숲,서울특별시 성동구 왕십리로,재즈 공연
                잘못된 좌표,2026-06-01,2026-06-02,37.5,999.0,광장,서울특별시 중구 세종대로,
                """;

        FestivalImportService.ImportResult result = service.importCsv(
                "example.csv", csv.getBytes(StandardCharsets.UTF_8), "UTF-8");

        assertEquals(2, result.totalRows());
        assertEquals(1, result.successRows());
        assertEquals(1, result.skippedRows());
        assertEquals(List.of(new FestivalImportService.SkippedRow(3, "경도 범위 오류")), result.skipped());
        verify(festivals).upsert(anyString(), anyString(), any(), any(), nullable(String.class),
                nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(String.class), nullable(String.class), nullable(String.class),
                any(), any(), eq("MUSIC"));
        verify(history).insert("example.csv", 2, 1, 1);
    }

    @Test
    void rejectsMissingRequiredHeadersBeforeWritingHistory() {
        FestivalMapper festivals = mock(FestivalMapper.class);
        ImportHistoryMapper history = mock(ImportHistoryMapper.class);
        FestivalImportService service = new FestivalImportService(
                new FestivalImportTransactionService(
                        new FestivalCsvReader(), festivals, history, new TypeClassifier()));

        org.junit.jupiter.api.Assertions.assertThrows(InvalidFestivalCsvException.class,
                () -> service.importCsv("bad.csv", "이름\n축제".getBytes(StandardCharsets.UTF_8), "UTF-8"));

        verifyNoInteractions(festivals, history);
    }
}
