package com.map.egis.controller;

import com.map.egis.config.StorageProperties;
import com.map.egis.dto.PoiResponse;
import com.map.egis.dto.PoiWriteRequest;
import com.map.egis.service.CapsuleService;
import com.map.egis.service.PoiShareService;
import com.map.egis.service.MediaService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PoiController {
    private final CapsuleService capsuleService;
    private final MediaService mediaService;
    private final PoiShareService shareService;
    private final StorageProperties storageProperties;
    @Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    @GetMapping("/api/pois/bbox")
    public List<PoiResponse> pois(@RequestParam Double minLng, @RequestParam Double minLat,
                                  @RequestParam Double maxLng, @RequestParam Double maxLat,
                                  @RequestParam(required = false) List<Long> groupIds,
                                  HttpServletRequest request) {
        return capsuleService.poisInBounds(userId(request), minLng, minLat, maxLng, maxLat, groupIds);
    }

    @PostMapping("/api/pois")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public PoiResponse create(@RequestParam Long groupId, @RequestParam String title,
                              @RequestParam(required = false) String content,
                              @RequestParam("image") MultipartFile image,
                              HttpServletRequest request) {
        PoiWriteRequest body = new PoiWriteRequest();
        body.setCapsuleId(groupId);
        body.setTitle(title);
        body.setContent(content);
        return capsuleService.createPoi(userId(request), body, image);
    }

    @PostMapping("/api/pois/gps")
    public MediaService.Gps previewGps(@RequestParam("image") MultipartFile image, HttpServletRequest request) {
        userId(request);
        return mediaService.previewGps(image);
    }

    @GetMapping("/api/pois/{poiId}")
    public PoiResponse get(@PathVariable long poiId, HttpServletRequest request) {
        return capsuleService.getPoi(poiId, userId(request));
    }

    @PutMapping("/api/pois/{poiId}")
    public PoiResponse update(@PathVariable long poiId, @RequestParam(required = false) Long groupId,
                              @RequestParam String title, @RequestParam(required = false) String content,
                              @RequestParam(required = false) MultipartFile image,
                              HttpServletRequest request) {
        PoiWriteRequest body = new PoiWriteRequest();
        body.setCapsuleId(groupId);
        body.setTitle(title);
        body.setContent(content);
        return capsuleService.updatePoi(poiId, userId(request), body, image);
    }

    @DeleteMapping("/api/pois/{poiId}")
    public Map<String, Object> delete(@PathVariable long poiId, HttpServletRequest request) {
        capsuleService.deletePoi(poiId, userId(request));
        return Map.of("success", true);
    }

    @GetMapping("/api/pois/{poiId}/history")
    public List<Map<String, Object>> history(@PathVariable long poiId, HttpServletRequest request) {
        return capsuleService.poiHistory(poiId, userId(request));
    }

    @PostMapping("/api/pois/{poiId}/share")
    public Map<String, String> share(@PathVariable long poiId, HttpServletRequest request) {
        String token = shareService.createShare(poiId, userId(request));
        String base = publicBaseUrl.trim().replaceAll("/+$", "");
        return Map.of("url", base + "/shared/" + token);
    }

    @DeleteMapping("/api/pois/{poiId}/share")
    public Map<String, Object> revokeShare(@PathVariable long poiId, HttpServletRequest request) {
        shareService.revokeShares(poiId, userId(request));
        return Map.of("success", true);
    }

    @GetMapping("/api/pois/{poiId}/image")
    public ResponseEntity<Resource> image(@PathVariable long poiId, HttpServletRequest request) throws MalformedURLException {
        PoiResponse poi = capsuleService.getPoi(poiId, userId(request));
        return resource(poi.getOriginalFilePath(), true);
    }

    @GetMapping("/api/pois/{poiId}/thumb")
    public ResponseEntity<Resource> thumb(@PathVariable long poiId, HttpServletRequest request) throws MalformedURLException {
        PoiResponse poi = capsuleService.getPoi(poiId, userId(request));
        return resource(poi.getCompressedFilePath(), false);
    }

    @GetMapping("/api/shared/{token}")
    public PoiResponse shared(@PathVariable String token) {
        return shareService.sharedPoi(token);
    }

    @GetMapping("/api/shared/{token}/image")
    public ResponseEntity<Resource> sharedImage(@PathVariable String token) throws MalformedURLException {
        PoiResponse poi = shareService.sharedPoi(token);
        return resource(poi.getOriginalFilePath(), true, true);
    }

    private ResponseEntity<Resource> resource(String storedPath, boolean original) throws MalformedURLException {
        return resource(storedPath, original, false);
    }

    private ResponseEntity<Resource> resource(String storedPath, boolean original, boolean noStore) throws MalformedURLException {
        String prefix = original ? "/images/original/" : "/images/compressed/";
        Path root = original ? storageProperties.originalDirectory() : storageProperties.compressedDirectory();
        if (storedPath == null || !storedPath.startsWith(prefix)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Path path = root.resolve(storedPath.substring(prefix.length())).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Resource resource = new UrlResource(path.toUri());
        String type = original ? contentType(storedPath) : "image/webp";
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, noStore ? "no-store" : "private, max-age=300")
                .contentType(MediaType.parseMediaType(type)).body(resource);
    }

    private String contentType(String path) {
        return path.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
    }

    private String userId(HttpServletRequest request) {
        Object userId = request.getAttribute("authenticatedUserId");
        if (!(userId instanceof String value)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return value;
    }
}
