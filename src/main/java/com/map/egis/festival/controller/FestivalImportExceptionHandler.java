package com.map.egis.festival.controller;

import com.map.egis.festival.importer.ImportInProgressException;
import com.map.egis.festival.importer.InvalidFestivalCsvException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice(assignableTypes = AdminImportController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class FestivalImportExceptionHandler {
    @ExceptionHandler(InvalidFestivalCsvException.class)
    public ResponseEntity<Map<String, String>> invalidFile(InvalidFestivalCsvException error) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_FILE", "message", error.getMessage()));
    }

    @ExceptionHandler(ImportInProgressException.class)
    public ResponseEntity<Map<String, String>> importInProgress(ImportInProgressException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "IMPORT_IN_PROGRESS", "message", error.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> accessDenied(ResponseStatusException error) {
        HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
        String code = status == HttpStatus.UNAUTHORIZED ? "UNAUTHORIZED" : "FORBIDDEN";
        String message = status == HttpStatus.UNAUTHORIZED ? "로그인이 필요합니다." : "관리자 권한이 필요합니다.";
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> internalError(Exception error, HttpServletRequest request) {
        log.error("Festival import request failed: {}", request.getRequestURI(), error);
        return ResponseEntity.internalServerError().body(Map.of(
                "code", "INTERNAL_ERROR", "message", "축제 데이터 적재를 완료하지 못했습니다."));
    }
}
