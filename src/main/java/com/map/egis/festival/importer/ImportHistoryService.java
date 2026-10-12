package com.map.egis.festival.importer;

import com.map.egis.festival.dto.ImportHistoryDto;
import com.map.egis.festival.mapper.ImportHistoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ImportHistoryService {
    private final ImportHistoryMapper importHistoryMapper;

    public ImportHistoryService(ImportHistoryMapper importHistoryMapper) {
        this.importHistoryMapper = importHistoryMapper;
    }

    @Transactional(readOnly = true)
    public List<ImportHistoryDto> recent() {
        return importHistoryMapper.findRecent();
    }
}
