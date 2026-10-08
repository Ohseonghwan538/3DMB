package com.map.egis.controller;

import com.map.egis.service.MediaService;
import com.map.egis.mapper.AdminMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {
    private final MediaService mediaService;
    private final AdminMapper adminMapper;

    @PostMapping("/images")
    public MediaService.UploadedImage upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        if (request.getAttribute("authenticatedUserId") == null) throw new UnauthorizedException();
        try {
            return mediaService.store(file);
        } catch (IllegalArgumentException error) {
            adminMapper.insertError("UPLOAD_001", "UPLOAD", error.getMessage());
            throw error;
        } catch (IllegalStateException error) {
            adminMapper.insertError("UPLOAD_002", "UPLOAD", "사진 저장 또는 변환에 실패했습니다.");
            throw error;
        }
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    private static class UnauthorizedException extends RuntimeException { }
}
