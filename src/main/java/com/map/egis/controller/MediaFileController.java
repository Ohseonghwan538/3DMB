package com.map.egis.controller;

import com.map.egis.config.StorageProperties;
import com.map.egis.mapper.CapsuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import jakarta.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.nio.file.Path;

/** Serves saved media and gives legacy, missing records a harmless visual fallback. */
@RestController
@RequiredArgsConstructor
public class MediaFileController {
    private final StorageProperties storageProperties;
    private final CapsuleMapper capsuleMapper;

    @GetMapping({"/images/original/**", "/images/compressed/**"})
    public ResponseEntity<Resource> image(HttpServletRequest request) throws MalformedURLException {
        String uri = request.getRequestURI();
        boolean compressed = uri.startsWith("/images/compressed/");
        String prefix = compressed ? "/images/compressed/" : "/images/original/";
        String relativePath = uri.substring(prefix.length()).replace('\\', '/');
        Path root = compressed ? storageProperties.compressedDirectory() : storageProperties.originalDirectory();
        Path resolved = root.resolve(relativePath).normalize();
        String userId = (String) request.getAttribute("authenticatedUserId");
        if (userId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        String role = String.valueOf(request.getAttribute("authenticatedUserRole"));
        boolean admin = role.equals("ADMIN") || role.equals("SUPER_ADMIN") || role.equals("MODERATOR");
        String storedPath = prefix + relativePath;
        if (!resolved.startsWith(root) || capsuleMapper.canReadImagePath(storedPath, userId, admin) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        Resource resource = new UrlResource(resolved.toUri());
        if (resource.exists() && resource.isReadable()) {
            return ResponseEntity.ok().header("Cache-Control", "private").contentType(contentType(relativePath)).body(resource);
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private org.springframework.http.MediaType contentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".webp")) return org.springframework.http.MediaType.parseMediaType("image/webp");
        if (lower.endsWith(".png")) return org.springframework.http.MediaType.IMAGE_PNG;
        return org.springframework.http.MediaType.IMAGE_JPEG;
    }
}
