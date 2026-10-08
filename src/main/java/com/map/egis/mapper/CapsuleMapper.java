package com.map.egis.mapper;

import com.map.egis.dto.CapsuleCreateRequest;
import com.map.egis.dto.CapsuleResponse;
import com.map.egis.dto.PoiResponse;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CapsuleMapper {
    // capsules 저장 (자동 생성된 PK가 request 객체 내부 필드에 세팅됨)
    void insertCapsule(CapsuleCreateRequest request);

    // capsule_images 일괄 저장 (Batch Insert)
    void insertCapsuleImages(
        @Param("capsuleId") long capsuleId, 
        @Param("images") List<CapsuleCreateRequest.ImageDto> images
    );
    
    // 화면 영영(Bounding Box) 범위 내 캡슐 및 이미지 조인 조회
    List<CapsuleResponse> selectCapsulesInBounds(
        @Param("userId") String userId,
        @Param("minLng") Double minLng,
        @Param("minLat") Double minLat,
        @Param("maxLng") Double maxLng,
        @Param("maxLat") Double maxLat
    );
    List<CapsuleResponse> selectCapsulesByUserId(@Param("userId") String userId);
    int countCapsuleByIdAndUserId(@Param("capsuleId") long capsuleId, @Param("userId") String userId);

    int canReadImagePath(@Param("path") String path, @Param("userId") String userId, @Param("admin") boolean admin);
    int appendMemo(@Param("capsuleId") long capsuleId, @Param("userId") String userId,
                   @Param("memo") String memo, @Param("longitude") Double longitude,
                   @Param("latitude") Double latitude, @Param("altitude") Double altitude);

    List<PoiResponse> selectPoisInBounds(@Param("userId") String userId,
                                         @Param("minLng") Double minLng, @Param("minLat") Double minLat,
                                         @Param("maxLng") Double maxLng, @Param("maxLat") Double maxLat,
                                         @Param("groupIds") List<Long> groupIds);
    PoiResponse selectPoi(@Param("poiId") long poiId, @Param("userId") String userId);
    PoiResponse selectPoiForUpdate(@Param("poiId") long poiId, @Param("userId") String userId);
    List<java.util.Map<String, Object>> selectPoiHistory(@Param("poiId") long poiId, @Param("userId") String userId);
    int canReadPoiHistory(@Param("poiId") long poiId, @Param("userId") String userId);
    int archivePoi(@Param("poiId") long poiId, @Param("userId") String userId,
                   @Param("changedBy") String changedBy, @Param("changeType") String changeType);
    int updatePoi(@Param("poiId") long poiId, @Param("userId") String userId,
                  @Param("title") String title, @Param("content") String content,
                  @Param("groupId") long groupId, @Param("originalPath") String originalPath,
                  @Param("compressedPath") String compressedPath);
    int deletePoi(@Param("poiId") long poiId, @Param("userId") String userId);
    int updateGroup(@Param("groupId") long groupId, @Param("userId") String userId,
                    @Param("name") String name, @Param("color") String color);
    int deleteGroup(@Param("groupId") long groupId, @Param("userId") String userId);
    int groupHasPois(@Param("groupId") long groupId, @Param("userId") String userId);
    List<java.util.Map<String, Object>> selectGroups(@Param("userId") String userId);
    int updateGroupVisibility(@Param("groupId") long groupId, @Param("userId") String userId,
                              @Param("visibility") String visibility);
    void setGroupColor(@Param("groupId") long groupId, @Param("userId") String userId, @Param("color") String color);
    void setGroupName(@Param("groupId") long groupId, @Param("userId") String userId, @Param("name") String name);
    int insertPoi(@Param("capsuleId") long capsuleId, @Param("userId") String userId,
                  @Param("title") String title, @Param("content") String content,
                  @Param("originalPath") String originalPath, @Param("compressedPath") String compressedPath,
                  @Param("longitude") Double longitude, @Param("latitude") Double latitude,
                  @Param("altitude") Double altitude);
    long selectPoiIdByPath(@Param("originalPath") String originalPath, @Param("userId") String userId);

    int insertShare(@Param("poiId") long poiId, @Param("userId") String userId, @Param("tokenHash") String tokenHash);
    int revokeShares(@Param("poiId") long poiId, @Param("userId") String userId);
    PoiResponse selectSharedPoi(@Param("tokenHash") String tokenHash);
}
