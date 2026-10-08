package com.map.egis.service;

import com.map.egis.config.StorageProperties;
import com.map.egis.mapper.AdminMapper;
import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AdminService {
    private static final Set<String> ROLES = Set.of("SUPER_ADMIN", "ADMIN", "MODERATOR", "USER");
    private static final Set<String> ACCOUNT_STATUSES = Set.of("ACTIVE", "SUSPENDED", "WITHDRAWN");
    private static final Set<String> DEFECT_STATUSES = Set.of("HOLD", "IN_PROGRESS", "DONE");

    private final AdminMapper adminMapper;
    private final StorageProperties storageProperties;

    public Map<String, Object> userSummary() {
        return adminMapper.selectUserSummary();
    }

    public List<Map<String, Object>> users(String query, String status, String role, boolean locked, int page) {
        validateFilter(status, role, page);
        return adminMapper.selectUsers(clean(query), status, role, locked, (page - 1) * 20, 20);
    }

    public Map<String, Object> user(String userId) {
        Map<String, Object> user = adminMapper.selectUser(userId);
        if (user == null) throw new IllegalArgumentException("회원을 찾을 수 없습니다.");
        return user;
    }

    @Transactional
    public void updateUser(String actorId, String actorRole, String userId, String status, String role) {
        Map<String, Object> before = user(userId);
        boolean changeStatus = status != null && !status.equals(before.get("status"));
        boolean changeRole = role != null && !role.equals(before.get("role"));
        if (!changeStatus && !changeRole) return;
        if (actorId.equals(userId)) throw new IllegalArgumentException("본인의 상태 또는 권한은 변경할 수 없습니다.");
        if (status != null && !ACCOUNT_STATUSES.contains(status)) throw new IllegalArgumentException("잘못된 회원 상태입니다.");
        if (role != null && !ROLES.contains(role)) throw new IllegalArgumentException("잘못된 권한입니다.");
        if (changeRole && !"SUPER_ADMIN".equals(actorRole)) throw new SecurityException("SUPER_ADMIN만 권한을 변경할 수 있습니다.");
        if ("SUPER_ADMIN".equals(before.get("role")) && (changeRole || "ACTIVE".equals(before.get("status")) && status != null && !"ACTIVE".equals(status))
                && adminMapper.countActiveSuperAdmins() <= 1) {
            throw new IllegalArgumentException("마지막 SUPER_ADMIN의 권한 또는 상태는 변경할 수 없습니다.");
        }
        if (status != null) adminMapper.updateAccountStatus(userId, status);
        if (role != null && changeRole) adminMapper.updateRole(userId, role);
        audit(actorId, "USER_UPDATE", "USER", userId, "status=" + status + ", role=" + role);
    }

    @Transactional
    public void unlockUser(String actorId, String userId) {
        if (adminMapper.unlockUser(userId) == 0) throw new IllegalArgumentException("회원을 찾을 수 없습니다.");
        audit(actorId, "USER_UNLOCK", "USER", userId, "로그인 실패 횟수를 초기화했습니다.");
    }

    public Map<String, Object> contentSummary() {
        Map<String, Object> summary = new java.util.HashMap<>(adminMapper.selectContentSummary());
        Map<String, Object> storage = storageStatus();
        summary.put("originalBytes", storage.get("originalBytes"));
        summary.put("compressedBytes", storage.get("compressedBytes"));
        return summary;
    }

    public List<Map<String, Object>> content(String tab, String query, int page) {
        if (page < 1 || page > 10000) throw new IllegalArgumentException("페이지 값이 올바르지 않습니다.");
        String normalized = clean(query);
        int offset = (page - 1) * 20;
        return switch (tab) {
            case "groups" -> adminMapper.selectGroups(normalized, offset, 20);
            case "photos" -> adminMapper.selectPhotos(normalized, offset, 20);
            case "memos" -> adminMapper.selectMemos(normalized, offset, 20);
            default -> throw new IllegalArgumentException("지원하지 않는 콘텐츠 유형입니다.");
        };
    }

    @Transactional
    public void setGroupVisibility(String actorId, long groupId, String visibility) {
        validateVisibility(visibility);
        if (adminMapper.setGroupVisibility(groupId, visibility) == 0) throw new IllegalArgumentException("추억 그룹을 찾을 수 없습니다.");
        audit(actorId, "GROUP_VISIBILITY", "GROUP", Long.toString(groupId), visibility);
    }

    @Transactional
    public void deleteGroup(String actorId, long groupId) {
        if (adminMapper.deleteGroup(groupId) == 0) throw new IllegalArgumentException("추억 그룹을 찾을 수 없습니다.");
        audit(actorId, "GROUP_DELETE", "GROUP", Long.toString(groupId), "소프트 삭제 및 파일 정리 대상으로 이동");
    }

    @Transactional
    public void setPhotoVisibility(String actorId, long imageId, String visibility) {
        validateVisibility(visibility);
        if (adminMapper.setPhotoVisibility(imageId, visibility) == 0) throw new IllegalArgumentException("사진을 찾을 수 없습니다.");
        audit(actorId, "PHOTO_VISIBILITY", "PHOTO", Long.toString(imageId), visibility);
    }

    @Transactional
    public void deletePhoto(String actorId, long imageId) {
        if (adminMapper.deletePhoto(imageId) == 0) throw new IllegalArgumentException("사진을 찾을 수 없습니다.");
        audit(actorId, "PHOTO_DELETE", "PHOTO", Long.toString(imageId), "소프트 삭제 및 파일 정리 대상으로 이동");
    }

    @Transactional
    public void setMemoVisibility(String actorId, long groupId, String visibility) {
        validateVisibility(visibility);
        if (adminMapper.setMemoVisibility(groupId, visibility) == 0) throw new IllegalArgumentException("메모를 찾을 수 없습니다.");
        audit(actorId, "MEMO_VISIBILITY", "MEMO", Long.toString(groupId), visibility);
    }

    @Transactional
    public void deleteMemo(String actorId, long groupId) {
        if (adminMapper.deleteMemo(groupId) == 0) throw new IllegalArgumentException("메모를 찾을 수 없습니다.");
        audit(actorId, "MEMO_DELETE", "MEMO", Long.toString(groupId), "소프트 삭제");
    }

    public Map<String, Object> dashboardSummary(int days) {
        validateDays(days);
        return adminMapper.selectDashboardSummary(days);
    }

    public List<Map<String, Object>> errorCounts(int days) {
        validateDays(days);
        return adminMapper.selectErrorCounts(days);
    }

    public List<Map<String, Object>> errorDetails(String code, int days) {
        validateDays(days);
        if (code == null || !code.matches("[A-Z]+_[0-9]{3}")) throw new IllegalArgumentException("에러 코드 형식이 올바르지 않습니다.");
        return adminMapper.selectErrorDetails(code, days);
    }

    public List<Map<String, Object>> defects() {
        return adminMapper.selectDefects();
    }

    @Transactional
    public void updateDefect(String actorId, String defectId, String status) {
        if (status == null || !DEFECT_STATUSES.contains(status)) throw new IllegalArgumentException("잘못된 결함 상태입니다.");
        if (adminMapper.updateDefectStatus(defectId, status) == 0) throw new IllegalArgumentException("결함을 찾을 수 없습니다.");
        audit(actorId, "DEFECT_STATUS", "DEFECT", defectId, status);
    }

    public Map<String, Object> storageStatus() {
        List<Map<String, Object>> linked = adminMapper.selectLinkedFiles();
        Set<String> referencedOriginal = new HashSet<>();
        Set<String> referencedCompressed = new HashSet<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        long originalBytes = 0;
        long compressedBytes = 0;
        for (Map<String, Object> file : linked) {
            String value = String.valueOf(file.get("path"));
            boolean original = "original".equals(file.get("kind"));
            Path path = resolveStoredPath(value, original);
            boolean exists = Files.isRegularFile(path);
            long size = size(path);
            if (original) {
                referencedOriginal.add(path.toAbsolutePath().normalize().toString());
                originalBytes += size;
            } else {
                referencedCompressed.add(path.toAbsolutePath().normalize().toString());
                compressedBytes += size;
            }
            rows.add(Map.of("path", value, "kind", original ? "original" : "compressed",
                    "size", size, "status", exists ? "OK" : "MISSING"));
        }

        List<Map<String, Object>> orphans = new ArrayList<>();
        collectOrphans(storageProperties.originalDirectory(), "original", referencedOriginal, orphans);
        collectOrphans(storageProperties.compressedDirectory(), "compressed", referencedCompressed, orphans);
        long compressedMissing = rows.stream().filter(row -> "compressed".equals(row.get("kind")) && "MISSING".equals(row.get("status"))).count();
        return Map.of("originalBytes", originalBytes, "compressedBytes", compressedBytes,
                "files", rows, "orphans", orphans, "orphanCount", orphans.size(),
                "missingCompressed", compressedMissing);
    }

    @Transactional
    public int recompressMissing(String actorId) {
        int rebuilt = 0;
        for (Map<String, Object> item : adminMapper.selectImagePairs()) {
            Path target = resolveStoredPath(String.valueOf(item.get("compressedPath")), false);
            if (Files.isRegularFile(target)) continue;
            String compressedPath = String.valueOf(item.get("compressedPath"));
            Path source = resolveStoredPath(String.valueOf(item.get("originalPath")), true);
            if (!Files.isRegularFile(source)) continue;
            try {
                Files.createDirectories(target.getParent());
                Thumbnails.of(source.toFile()).size(1600, 1600).outputFormat("webp").outputQuality(0.82).toFile(target.toFile());
                rebuilt++;
            } catch (IOException error) {
                throw new IllegalStateException("압축 이미지 재생성에 실패했습니다: " + compressedPath, error);
            }
        }
        audit(actorId, "STORAGE_RECOMPRESS", "STORAGE", null, "재생성 " + rebuilt + "개");
        return rebuilt;
    }

    @Transactional
    public int deleteOrphans(String actorId) {
        Map<String, Object> state = storageStatus();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> orphans = (List<Map<String, Object>>) state.get("orphans");
        int deleted = 0;
        for (Map<String, Object> orphan : orphans) {
            Path path = resolveStoredPath(String.valueOf(orphan.get("path")), "original".equals(orphan.get("kind")));
            try {
                if (Files.deleteIfExists(path)) deleted++;
            } catch (IOException error) {
                throw new IllegalStateException("고아 파일을 정리하지 못했습니다: " + orphan.get("path"), error);
            }
        }
        audit(actorId, "STORAGE_ORPHANS_DELETE", "STORAGE", null, "삭제 " + deleted + "개");
        return deleted;
    }

    private void collectOrphans(Path root, String kind, Set<String> referenced, List<Map<String, Object>> result) {
        if (!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                Path normalized = path.toAbsolutePath().normalize();
                if (!referenced.contains(normalized.toString())) {
                    result.add(Map.of("path", storedUrl(normalized, root, kind), "kind", kind, "size", size(normalized), "status", "ORPHAN"));
                }
            });
        } catch (IOException error) {
            throw new IllegalStateException("저장소 파일을 확인하지 못했습니다: " + root, error);
        }
    }

    private Path resolveStoredPath(String stored, boolean original) {
        String prefix = original ? "/images/original/" : "/images/compressed/";
        if (!stored.startsWith(prefix)) throw new IllegalArgumentException("잘못된 저장소 경로입니다.");
        Path root = original ? storageProperties.originalDirectory() : storageProperties.compressedDirectory();
        Path resolved = root.resolve(stored.substring(prefix.length())).normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("저장소 경로가 허용 범위를 벗어났습니다.");
        return resolved;
    }

    private String storedUrl(Path file, Path root, String kind) {
        return "/images/" + kind + "/" + root.relativize(file).toString().replace('\\', '/');
    }

    private long size(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.size(path) : 0L;
        } catch (IOException error) {
            throw new IllegalStateException("파일 크기를 확인하지 못했습니다: " + path, error);
        }
    }

    private void audit(String actorId, String action, String type, String targetId, String details) {
        adminMapper.insertAudit(actorId, action, type, targetId, details);
    }

    private void validateFilter(String status, String role, int page) {
        if (page < 1 || page > 10000) throw new IllegalArgumentException("페이지 값이 올바르지 않습니다.");
        if (!status.isEmpty() && !ACCOUNT_STATUSES.contains(status)) throw new IllegalArgumentException("잘못된 회원 상태입니다.");
        if (!role.isEmpty() && !ROLES.contains(role)) throw new IllegalArgumentException("잘못된 권한입니다.");
    }

    private void validateVisibility(String visibility) {
        if (!"PUBLIC".equals(visibility) && !"PRIVATE".equals(visibility)) throw new IllegalArgumentException("잘못된 공개 상태입니다.");
    }

    private void validateDays(int days) {
        if (days != 1 && days != 7 && days != 30) throw new IllegalArgumentException("기간은 1일, 7일 또는 30일이어야 합니다.");
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
