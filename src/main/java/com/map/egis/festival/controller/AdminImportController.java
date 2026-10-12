package com.map.egis.festival.controller;

import com.map.egis.festival.dto.ImportHistoryDto;
import com.map.egis.festival.importer.FestivalImportService;
import com.map.egis.festival.importer.ImportHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/admin/api")
public class AdminImportController {
    private final FestivalImportService festivalImportService;
    private final ImportHistoryService importHistoryService;

    public AdminImportController(FestivalImportService festivalImportService,
                                 ImportHistoryService importHistoryService) {
        this.festivalImportService = festivalImportService;
        this.importHistoryService = importHistoryService;
    }

    @PostMapping("/import")
    public FestivalImportService.ImportResult importCsv(@RequestParam("file") MultipartFile file,
                                                        @RequestParam(defaultValue = "MS949") String encoding,
                                                        HttpServletRequest request) throws IOException {
        requireAdmin(request);
        if (file == null || file.isEmpty()) {
            throw new com.map.egis.festival.importer.InvalidFestivalCsvException("CSV 파일이 비어 있습니다.");
        }
        return festivalImportService.importCsv(file.getOriginalFilename(), file.getBytes(), encoding);
    }

    @GetMapping("/imports")
    public List<ImportHistoryDto> imports(HttpServletRequest request) {
        requireAdmin(request);
        return importHistoryService.recent();
    }

    private void requireAdmin(HttpServletRequest request) {
        Object role = request.getAttribute("authenticatedUserRole");
        Object userId = request.getAttribute("authenticatedUserId");
        if (!(userId instanceof String) || !(role instanceof String roleName)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (!List.of("ADMIN", "SUPER_ADMIN").contains(roleName)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
