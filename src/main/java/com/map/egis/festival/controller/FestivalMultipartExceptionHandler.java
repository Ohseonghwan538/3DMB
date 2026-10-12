package com.map.egis.festival.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FestivalMultipartExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> oversizedFile(MaxUploadSizeExceededException error) {
        return ResponseEntity.badRequest().body(Map.of(
                "code", "INVALID_FILE", "message", "파일 크기는 5MB를 초과할 수 없습니다."));
    }
}
