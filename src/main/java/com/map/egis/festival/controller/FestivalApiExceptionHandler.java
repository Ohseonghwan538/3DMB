package com.map.egis.festival.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

@RestControllerAdvice(assignableTypes = {FestivalController.class, FestivalLookupController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class FestivalApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidParameter(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of(
                "code", "INVALID_PARAMETER", "message", error.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> invalidPathParameter(MethodArgumentTypeMismatchException error) {
        return ResponseEntity.badRequest().body(Map.of(
                "code", "INVALID_PARAMETER", "message", "요청 파라미터 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> internalError(Exception error, HttpServletRequest request) {
        log.error("Festival API request failed: {}", request.getRequestURI(), error);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "code", "INTERNAL_ERROR", "message", "요청을 처리하지 못했습니다."));
    }
}
