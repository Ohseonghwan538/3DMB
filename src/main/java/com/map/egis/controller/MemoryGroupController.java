package com.map.egis.controller;

import com.map.egis.dto.CapsuleCreateRequest;
import com.map.egis.service.CapsuleService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class MemoryGroupController {
    private final CapsuleService capsuleService;

    @GetMapping
    public List<Map<String, Object>> groups(HttpServletRequest request) {
        return capsuleService.groups(userId(request));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody GroupRequest request, HttpServletRequest httpRequest) {
        if (request == null || request.name() == null || request.name().isBlank() || request.name().trim().length() > 50
                || request.color() != null && !request.color().matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("묶음 이름(1~50자)과 올바른 #RRGGBB 색상을 입력해 주세요.");
        }
        CapsuleCreateRequest group = new CapsuleCreateRequest();
        group.setUserId(userId(httpRequest));
        group.setTitle(request.name().trim());
        String color = request.color() == null ? "#4263EB" : request.color().toUpperCase();
        group.setColor(color);
        group.setStartedAt(java.time.OffsetDateTime.now());
        long groupId = capsuleService.createCapsule(group);
        return Map.of("groupId", groupId, "name", request.name().trim(), "color", color);
    }

    @PutMapping("/{groupId}")
    public Map<String, Object> update(@PathVariable long groupId, @RequestBody GroupRequest request,
                                      HttpServletRequest httpRequest) {
        if (request == null) throw new IllegalArgumentException("묶음 정보가 필요합니다.");
        capsuleService.updateGroup(groupId, userId(httpRequest), request.name(), request.color());
        return Map.of("success", true);
    }

    @PatchMapping("/{groupId}/visibility")
    public Map<String, Object> visibility(@PathVariable long groupId, @RequestBody VisibilityRequest request,
                                          HttpServletRequest httpRequest) {
        if (request == null) throw new IllegalArgumentException("공개 상태가 필요합니다.");
        capsuleService.setGroupVisibility(groupId, userId(httpRequest), request.visibility());
        return Map.of("success", true);
    }

    @DeleteMapping("/{groupId}")
    public Map<String, Object> delete(@PathVariable long groupId, HttpServletRequest request) {
        capsuleService.deleteGroup(groupId, userId(request));
        return Map.of("success", true);
    }

    private String userId(HttpServletRequest request) {
        Object userId = request.getAttribute("authenticatedUserId");
        if (!(userId instanceof String value)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return value;
    }

    public record GroupRequest(String name, String color) { }
    public record VisibilityRequest(String visibility) { }
}
