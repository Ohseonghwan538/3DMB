package com.map.egis.festival.importer;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class FestivalImportService {
    private final FestivalImportTransactionService transactionService;
    private final AtomicBoolean importing = new AtomicBoolean();

    public FestivalImportService(FestivalImportTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    public ImportResult importCsv(String originalFileName, byte[] bytes, String encoding) {
        if (!importing.compareAndSet(false, true)) throw new ImportInProgressException();
        try {
            return transactionService.importCsv(originalFileName, bytes, encoding);
        } finally {
            importing.set(false);
        }
    }

    public record SkippedRow(int row, String reason) { }
    public record ImportResult(String fileName, int totalRows, int successRows,
                               int skippedRows, List<SkippedRow> skipped) { }
}
