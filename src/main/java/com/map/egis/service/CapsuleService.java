package com.map.egis.service;

import com.map.egis.dto.CapsuleResponse;
import com.map.egis.dto.CapsuleSearchRequest;
import com.map.egis.dto.CapsuleCreateRequest;
import com.map.egis.dto.CapsuleEntryRequest;
import com.map.egis.dto.PoiResponse;
import com.map.egis.dto.PoiWriteRequest;
import com.map.egis.mapper.CapsuleMapper;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CapsuleService {

    private final CapsuleMapper capsuleMapper;
    private final MediaService mediaService;

    public CapsuleService(CapsuleMapper capsuleMapper, MediaService mediaService) {
        this.capsuleMapper = capsuleMapper;
        this.mediaService = mediaService;
    }

    @Transactional
    public PoiResponse createPoi(String userId, PoiWriteRequest request, MultipartFile file) {
        validatePoi(request, file);
        if (capsuleMapper.countCapsuleByIdAndUserId(request.getCapsuleId(), userId) == 0) {
            throw new IllegalArgumentException("추억 묶음을 찾을 수 없습니다.");
        }
        MediaService.UploadedImage stored = mediaService.store(file);
        try {
            Double longitude = stored.longitude();
            Double latitude = stored.latitude();
            Double altitude = stored.altitude() == null ? 0D : stored.altitude();
            if (longitude == null || latitude == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "GPS_NOT_FOUND: 사진에 위치 정보가 없습니다.");
            }
            if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                    || longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
                throw new IllegalArgumentException("사진 좌표가 유효한 경위도 범위를 벗어났습니다.");
            }
            int inserted = capsuleMapper.insertPoi(request.getCapsuleId(), userId, request.getTitle().trim(),
                    blankToNull(request.getContent()), stored.originalFilePath(), stored.compressedFilePath(),
                    longitude, latitude, altitude);
            if (inserted == 0) throw new IllegalArgumentException("추억 묶음을 찾을 수 없습니다.");
            long poiId = capsuleMapper.selectPoiIdByPath(stored.originalFilePath(), userId);
            return getPoi(poiId, userId);
        } catch (RuntimeException error) {
            mediaService.remove(stored);
            throw error;
        }
    }

    @Transactional
    public PoiResponse updatePoi(long poiId, String userId, PoiWriteRequest request, MultipartFile file) {
        if (request == null) throw new IllegalArgumentException("수정할 내용이 필요합니다.");
        validateTitleAndContent(request.getTitle(), request.getContent());
        PoiResponse current = capsuleMapper.selectPoiForUpdate(poiId, userId);
        if (current == null) throw new IllegalArgumentException("POI를 찾을 수 없습니다.");
        long groupId = request.getCapsuleId() == null ? current.getCapsuleId() : request.getCapsuleId();
        if (capsuleMapper.countCapsuleByIdAndUserId(groupId, userId) == 0) {
            throw new IllegalArgumentException("추억 묶음을 찾을 수 없습니다.");
        }
        MediaService.UploadedImage stored = null;
        if (file != null && !file.isEmpty()) stored = mediaService.store(file);
        try {
            if (capsuleMapper.archivePoi(poiId, userId, userId, "UPDATE") == 0) {
                throw new IllegalArgumentException("POI를 찾을 수 없습니다.");
            }
            int updated = capsuleMapper.updatePoi(poiId, userId, request.getTitle().trim(),
                    blankToNull(request.getContent()), groupId,
                    stored == null ? null : stored.originalFilePath(),
                    stored == null ? null : stored.compressedFilePath());
            if (updated == 0) throw new IllegalArgumentException("POI를 수정할 수 없습니다.");
            return getPoi(poiId, userId);
        } catch (RuntimeException error) {
            mediaService.remove(stored);
            throw error;
        }
    }

    @Transactional
    public void deletePoi(long poiId, String userId) {
        if (capsuleMapper.selectPoiForUpdate(poiId, userId) == null) throw new IllegalArgumentException("POI를 찾을 수 없습니다.");
        if (capsuleMapper.archivePoi(poiId, userId, userId, "DELETE") == 0
                || capsuleMapper.deletePoi(poiId, userId) == 0) {
            throw new IllegalArgumentException("POI를 삭제할 수 없습니다.");
        }
    }

    @Transactional(readOnly = true)
    public PoiResponse getPoi(long poiId, String userId) {
        PoiResponse poi = capsuleMapper.selectPoi(poiId, userId);
        if (poi == null) throw new IllegalArgumentException("POI를 찾을 수 없습니다.");
        poi.setImageUrl("/api/pois/" + poiId + "/image");
        poi.setThumbUrl("/api/pois/" + poiId + "/thumb");
        return poi;
    }

    @Transactional(readOnly = true)
    public List<PoiResponse> poisInBounds(String userId, Double minLng, Double minLat,
                                          Double maxLng, Double maxLat, List<Long> groupIds) {
        if (minLng == null || maxLng == null || minLat == null || maxLat == null
                || !Double.isFinite(minLng) || !Double.isFinite(maxLng)
                || !Double.isFinite(minLat) || !Double.isFinite(maxLat)
                || minLng < -180 || maxLng > 180 || minLat < -90 || maxLat > 90
                || minLng >= maxLng || minLat >= maxLat) {
            throw new IllegalArgumentException("지도 영역 좌표가 올바르지 않습니다.");
        }
        List<PoiResponse> pois = capsuleMapper.selectPoisInBounds(userId, minLng, minLat, maxLng, maxLat, groupIds);
        pois.forEach(poi -> {
            poi.setImageUrl("/api/pois/" + poi.getPoiId() + "/image");
            poi.setThumbUrl("/api/pois/" + poi.getPoiId() + "/thumb");
        });
        return pois;
    }

    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> poiHistory(long poiId, String userId) {
        if (capsuleMapper.canReadPoiHistory(poiId, userId) == 0) throw new IllegalArgumentException("POI를 찾을 수 없습니다.");
        return capsuleMapper.selectPoiHistory(poiId, userId);
    }

    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> groups(String userId) {
        return capsuleMapper.selectGroups(userId);
    }

    @Transactional
    public void updateGroup(long groupId, String userId, String name, String color) {
        if (name == null || name.isBlank() || name.trim().length() > 50) throw new IllegalArgumentException("묶음 이름은 1~50자여야 합니다.");
        if (color == null || !color.matches("#[0-9A-Fa-f]{6}")) throw new IllegalArgumentException("색상은 #RRGGBB 형식이어야 합니다.");
        if (capsuleMapper.updateGroup(groupId, userId, name.trim(), color.toUpperCase()) == 0) {
            throw new IllegalArgumentException("추억 묶음을 찾을 수 없습니다.");
        }
    }

    @Transactional
    public void deleteGroup(long groupId, String userId) {
        if (capsuleMapper.deleteGroup(groupId, userId) == 0) {
            throw new IllegalArgumentException(capsuleMapper.groupHasPois(groupId, userId) > 0
                    ? "POI가 있는 묶음은 삭제할 수 없습니다. 먼저 POI를 이동하거나 삭제하세요."
                    : "추억 묶음을 찾을 수 없습니다.");
        }
    }

    @Transactional
    public void setGroupVisibility(long groupId, String userId, String visibility) {
        if (!"PUBLIC".equals(visibility) && !"PRIVATE".equals(visibility)) throw new IllegalArgumentException("공개 상태가 올바르지 않습니다.");
        if (capsuleMapper.updateGroupVisibility(groupId, userId, visibility) == 0) throw new IllegalArgumentException("추억 묶음을 찾을 수 없습니다.");
    }

    private void validatePoi(PoiWriteRequest request, MultipartFile file) {
        if (request == null || request.getCapsuleId() == null) throw new IllegalArgumentException("추억 묶음을 선택해 주세요.");
        validateTitleAndContent(request.getTitle(), request.getContent());
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("사진을 선택해 주세요.");
    }

    private void validateTitleAndContent(String title, String content) {
        if (title == null || title.isBlank() || title.trim().length() > 100) throw new IllegalArgumentException("제목은 1~100자여야 합니다.");
        if (content != null && content.length() > 2000) throw new IllegalArgumentException("내용은 2,000자까지 입력할 수 있습니다.");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional
    public long createCapsule(CapsuleCreateRequest request) {
        validate(request);
        // 1. capsules 테이블 저장 (부모)
        capsuleMapper.insertCapsule(request);
        
        // keyProperty로 세팅된 capsule_id 획득
        long generatedCapsuleId = request.getCapsuleId(); 

        // 2. capsule_images 테이블 저장 (자식 - 첨부 이미지가 있는 경우)
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            capsuleMapper.insertCapsuleImages(generatedCapsuleId, request.getImages());
        }

        return generatedCapsuleId;
    }

    @Transactional
    public void appendEntry(long capsuleId, CapsuleEntryRequest request) {
        if (request == null || request.getUserId() == null || request.getUserId().isBlank()) throw new IllegalArgumentException("사용자 ID가 필요합니다.");
        String userId = request.getUserId().trim();
        if (capsuleMapper.countCapsuleByIdAndUserId(capsuleId, userId) == 0) throw new IllegalArgumentException("선택한 추억 묶음을 찾을 수 없습니다.");
        if (request.getImages() == null || request.getImages().isEmpty()) throw new IllegalArgumentException("저장할 사진이 필요합니다.");
        if (request.getImages().size() > 10) throw new IllegalArgumentException("한 번에 사진은 최대 10장까지 추가할 수 있습니다.");
        if (request.getLatitude() == null || request.getLongitude() == null
                || request.getLatitude() < -90 || request.getLatitude() > 90
                || request.getLongitude() < -180 || request.getLongitude() > 180) {
            throw new IllegalArgumentException("사진 GPS 정보가 없으면 지도에서 위치를 선택해 주세요.");
        }
        if (request.getMemo() != null && request.getMemo().length() > 5000) throw new IllegalArgumentException("기록은 5,000자까지 입력할 수 있습니다.");
        capsuleMapper.insertCapsuleImages(capsuleId, request.getImages());
        capsuleMapper.appendMemo(capsuleId, userId, request.getMemo() == null ? "" : request.getMemo().trim(),
                request.getLongitude(), request.getLatitude(), request.getAltitude());
    }

    private void validate(CapsuleCreateRequest request) {
        if (request == null || request.getUserId() == null || request.getUserId().isBlank()) throw new IllegalArgumentException("로그인 사용자 정보가 필요합니다.");
        if ((request.getLatitude() == null) != (request.getLongitude() == null)
                || request.getLatitude() != null && (request.getLatitude() < -90 || request.getLatitude() > 90
                || request.getLongitude() < -180 || request.getLongitude() > 180)) {
            throw new IllegalArgumentException("위도와 경도는 함께 입력하고 올바른 범위여야 합니다.");
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) request.setTitle("나의 3D 추억");
        request.setTitle(request.getTitle().trim().substring(0, Math.min(request.getTitle().trim().length(), 255)));
        if (request.getMemo() != null && request.getMemo().length() > 5000) throw new IllegalArgumentException("메모는 5,000자까지 입력할 수 있습니다.");
        if (request.getStartedAt() == null) request.setStartedAt(java.time.OffsetDateTime.now());
        if (request.getImages() != null && request.getImages().size() > 10) throw new IllegalArgumentException("한 캡슐에는 사진을 최대 10장까지 등록할 수 있습니다.");
    }

    @Transactional(readOnly = true)
    public List<CapsuleResponse> getCapsulesInBounds(CapsuleSearchRequest request) {
        if (request.getUserId() == null || request.getUserId().isBlank()) throw new IllegalArgumentException("사용자 ID가 필요합니다.");
        return capsuleMapper.selectCapsulesInBounds(
            request.getUserId().trim(),
            request.getMinLongitude(),
            request.getMinLatitude(),
            request.getMaxLongitude(),
            request.getMaxLatitude()
        );
    }

    @Transactional(readOnly = true)
    public List<CapsuleResponse> getCapsulesByUserId(String userId) {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("사용자 ID가 필요합니다.");
        return capsuleMapper.selectCapsulesByUserId(userId.trim());
    }
}
