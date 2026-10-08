package com.map.egis.controller;

import com.map.egis.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private static final Map<String, Integer> ROLE_LEVEL = Map.of(
            "USER", 0, "MODERATOR", 1, "ADMIN", 2, "SUPER_ADMIN", 3);
    private final AdminService adminService;

    @GetMapping("/users/summary")
    public Map<String, Object> userSummary(HttpServletRequest session) {
        requireRole(session, "ADMIN");
        return adminService.userSummary();
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users(@RequestParam(defaultValue = "") String q,
                                           @RequestParam(defaultValue = "") String status,
                                           @RequestParam(defaultValue = "") String role,
                                           @RequestParam(defaultValue = "false") boolean locked,
                                           @RequestParam(defaultValue = "1") int page,
                                           HttpServletRequest session) {
        requireRole(session, "ADMIN");
        return adminService.users(q, status, role, locked, page);
    }

    @GetMapping("/users/{userId}")
    public Map<String, Object> user(@PathVariable String userId, HttpServletRequest session) {
        requireRole(session, "ADMIN");
        return adminService.user(userId);
    }

    @PatchMapping("/users/{userId}")
    public Map<String, Object> updateUser(@PathVariable String userId, @RequestBody UserUpdate request,
                                          @RequestHeader(value = "X-CSRF-TOKEN", required = false) String csrf,
                                          HttpServletRequest session) {
        requireRole(session, "ADMIN");
        requireCsrf(session, csrf);
        if (request == null) throw new IllegalArgumentException("변경할 회원 정보가 필요합니다.");
        adminService.updateUser(userId(session), role(session), userId, request.status(), request.role());
        return Map.of("success", true);
    }

    @PostMapping("/users/{userId}/unlock")
    public Map<String, Object> unlock(@PathVariable String userId,
                                      @RequestHeader(value = "X-CSRF-TOKEN", required = false) String csrf,
                                      HttpServletRequest session) {
        requireRole(session, "ADMIN");
        requireCsrf(session, csrf);
        adminService.unlockUser(userId(session), userId);
        return Map.of("success", true);
    }

    @GetMapping("/content/summary")
    public Map<String, Object> contentSummary(HttpServletRequest session) {
        requireRole(session, "MODERATOR");
        return adminService.contentSummary();
    }

    @GetMapping("/content")
    public List<Map<String, Object>> content(@RequestParam(defaultValue = "groups") String tab,
                                             @RequestParam(defaultValue = "") String q,
                                             @RequestParam(defaultValue = "1") int page,
                                             HttpServletRequest session) {
        requireRole(session, "MODERATOR");
        return adminService.content(tab, q, page);
    }

    @PatchMapping("/groups/{groupId}/visibility")
    public Map<String, Object> groupVisibility(@PathVariable long groupId, @RequestBody VisibilityRequest request,
                                               @RequestHeader(value = "X-CSRF-TOKEN", required = false) String csrf,
                                               HttpServletRequest session) {
        requireRole(session, "MODERATOR");
        requireCsrf(session, csrf);
        if (request == null) throw new IllegalArgumentException("공개 상태가 필요합니다.");
        adminService.setGroupVisibility(userId(session), groupId, request.visibility());
        return Map.of("success", true);
    }

    @DeleteMapping("/groups/{groupId}")
    public Map<String, Object> deleteGroup(@PathVariable long groupId,
                                           @RequestHeader(value = "X-CSRF-TOKEN", required = false) String csrf,
                                           HttpServletRequest session) {
        requireRole(session, "MODERATOR");
        requireCsrf(session, csrf);
        adminService.deleteGroup(userId(session), groupId);
        return Map.of("success", true);
    }

    @PatchMapping("/photos/{imageId}/visibility")
    public Map<String, Object> photoVisibility(@PathVariable long imageId, @RequestBody VisibilityRequest request,
                                              @RequestHeader(value = "X-CSRF-TOKEN", required = false) String csrf,
                                              HttpServletRequest session) {
        requireRole(session, "MODERATOR");
        requireCsrf(session, csrf);
        if (request == null) throw new IllegalArgumentException("공개 상태가 필요합니다.");
        adminService.setPhotoVisibility(userId(session), imageId, request.visibility());
        return Map.of("success", true);
    }

    @DeleteMapping("/photos/{imageId}")
    public Map<String, Object> deletePhoto(@PathVariable long imageId,
                                           @RequestHeader(value = "X-CSRF-TOKEN", required = false) String csrf,
                                           HttpServletRequest session) {
        requireRole(session, "MODERATOR");
        requireCsrf(session, csrf);
        adminService.deletePhoto(userId(session), imageId);
        return Map.of("success", true);
    }

    @PatchMapping("/memos/{groupId}/visibility")
    public Map<String, Object> memoVisibility(@PathVariable long groupId, @RequestBody VisibilityRequest request,
                                              @RequestHeader(value = "X-CSRF-TOKEN", required = false) String csrf,
                                              HttpServletRequest session) {
        requireRole(session, "MODERATOR");
        requireCsrf(session, csrf);
        if (request == null) throw new IllegalArgumentException("공개 상태가 필요합니다.");
        adminService.setMemoVisibility(userId(session), groupId, request.visibility());
        return Map.of("success", true);
    }

    @DeleteMapping("/memos/{groupId}")
    public Map<String, Object> deleteMemo(@PathVariable long groupId,
                                         @RequestHeader(value = "X-CSRF-TOKEN", required = false) String csrf,
                                         HttpServletRequest session) {
        requireRole(session, "MODERATOR");
        requireCsrf(session, csrf);
        adminService.deleteMemo(userId(session), groupId);
        return Map.of("success", true);
    }

    @GetMapping("/storage")
    public Map<String, Object> storage(HttpServletRequest session) {
        requireRole(session, "MODERATOR");
        return adminService.storageStatus();
    }

    @PostMapping("/storage/recompress")
    public Map<String, Object> recompress(@RequestHeader(value = "X-CSRF-TOKEN", required = false) String csrf,
                                          HttpServletRequest session) {
        requireRole(session, "MODERATOR");
        requireCsrf(session, csrf);
        return Map.of("rebuilt", adminService.recompressMissing(userId(session)));
    }

    @DeleteMapping("/storage/orphans")
    public Map<String, Object> deleteOrphans(@RequestHeader(value = "X-CSRF-TOKEN", required = false) String csrf,
                                             HttpServletRequest session) {
        requireRole(session, "MODERATOR");
        requireCsrf(session, csrf);
        return Map.of("deleted", adminService.deleteOrphans(userId(session)));
    }

    @GetMapping("/dashboard/summary")
    public Map<String, Object> dashboardSummary(@RequestParam(defaultValue = "1") int days, HttpServletRequest session) {
        requireRole(session, "ADMIN");
        return adminService.dashboardSummary(days);
    }

    @GetMapping("/dashboard/errors")
    public List<Map<String, Object>> errors(@RequestParam(defaultValue = "1") int days, HttpServletRequest session) {
        requireRole(session, "ADMIN");
        return adminService.errorCounts(days);
    }

    @GetMapping("/dashboard/errors/{code}")
    public List<Map<String, Object>> errorDetails(@PathVariable String code,
                                                  @RequestParam(defaultValue = "1") int days,
                                                  HttpServletRequest session) {
        requireRole(session, "ADMIN");
        return adminService.errorDetails(code, days);
    }

    @GetMapping("/dashboard/defects")
    public List<Map<String, Object>> defects(HttpServletRequest session) {
        requireRole(session, "ADMIN");
        return adminService.defects();
    }

    @PatchMapping("/dashboard/defects/{defectId}")
    public Map<String, Object> updateDefect(@PathVariable String defectId, @RequestBody DefectUpdate request,
                                            @RequestHeader(value = "X-CSRF-TOKEN", required = false) String csrf,
                                            HttpServletRequest session) {
        requireRole(session, "ADMIN");
        requireCsrf(session, csrf);
        if (request == null) throw new IllegalArgumentException("결함 상태가 필요합니다.");
        adminService.updateDefect(userId(session), defectId, request.status());
        return Map.of("success", true);
    }

    private void requireRole(HttpServletRequest session, String required) {
        if (session.getAttribute("authenticatedUserId") == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        String role = role(session);
        Integer actual = ROLE_LEVEL.get(role);
        Integer minimum = ROLE_LEVEL.get(required);
        if (actual == null || actual < minimum) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }

    private void requireCsrf(HttpServletRequest session, String submitted) {
        Object value = session.getAttribute("authenticatedCsrfToken");
        if (!(value instanceof String token) || submitted == null ||
                !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), submitted.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "요청 검증 토큰이 올바르지 않습니다.");
        }
    }

    private String userId(HttpServletRequest session) {
        Object value = session.getAttribute("authenticatedUserId");
        if (!(value instanceof String userId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return userId;
    }

    private String role(HttpServletRequest session) {
        Object value = session.getAttribute("authenticatedUserRole");
        return value instanceof String role ? role : "USER";
    }

    public record UserUpdate(String status, String role) { }
    public record VisibilityRequest(String visibility) { }
    public record DefectUpdate(String status) { }
}
