package com.map.egis.controller;

import com.map.egis.dto.CapsuleResponse;
import com.map.egis.dto.CapsuleSearchRequest;
import com.map.egis.dto.CapsuleCreateRequest;
import com.map.egis.dto.CapsuleEntryRequest;
import com.map.egis.service.CapsuleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.HashMap;
import java.util.Map;


@RestController
@RequestMapping("/api/capsules")
public class CapsuleController {

    private final CapsuleService capsuleService;

    public CapsuleController(CapsuleService capsuleService) {
        this.capsuleService = capsuleService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createCapsule(@RequestBody CapsuleCreateRequest request, HttpServletRequest httpRequest) {
        request.setUserId(currentUserId(httpRequest));
        Long capsuleId = capsuleService.createCapsule(request);

        // JSON 객체 구조 생성
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "캡슐이 성공적으로 등록되었습니다.");
        response.put("data", capsuleId);

        return ResponseEntity.ok(response); // JSON 타입(application/json)으로 자동 직렬화 반환
    }

    @GetMapping
    public ResponseEntity<List<CapsuleResponse>> getCapsulesByUser(HttpServletRequest request) {
        return ResponseEntity.ok(capsuleService.getCapsulesByUserId(currentUserId(request)));
    }

    @PostMapping("/{capsuleId}/entries")
    public ResponseEntity<Map<String, Object>> appendEntry(@PathVariable long capsuleId, @RequestBody CapsuleEntryRequest request, HttpServletRequest httpRequest) {
        request.setUserId(currentUserId(httpRequest));
        capsuleService.appendEntry(capsuleId, request);
        return ResponseEntity.ok(Map.of("success", true, "capsuleId", capsuleId));
    }

    @GetMapping("/bounds")
    public ResponseEntity<List<CapsuleResponse>> getCapsulesInBounds(
            @RequestParam("minLng") Double minLng,
            @RequestParam("minLat") Double minLat,
            @RequestParam("maxLng") Double maxLng,
            @RequestParam("maxLat") Double maxLat,
            HttpServletRequest httpRequest) {

        CapsuleSearchRequest request = new CapsuleSearchRequest();
        request.setMinLongitude(minLng);
        request.setMinLatitude(minLat);
        request.setMaxLongitude(maxLng);
        request.setMaxLatitude(maxLat);
        request.setUserId(currentUserId(httpRequest));

        List<CapsuleResponse> capsules = capsuleService.getCapsulesInBounds(request);
        return ResponseEntity.ok(capsules);
    }

    private String currentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("authenticatedUserId");
        if (!(userId instanceof String value)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return value;
    }
}
