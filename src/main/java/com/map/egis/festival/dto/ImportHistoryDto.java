package com.map.egis.festival.dto;

import java.time.LocalDateTime;

public record ImportHistoryDto(long importId, String fileName, int totalRows,
                               int successRows, int skippedRows, LocalDateTime importedAt) {
}
