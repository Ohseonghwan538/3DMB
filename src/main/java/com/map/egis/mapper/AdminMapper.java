package com.map.egis.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminMapper {
    Map<String, Object> selectUserSummary();

    List<Map<String, Object>> selectUsers(@Param("query") String query,
                                          @Param("status") String status,
                                          @Param("role") String role,
                                          @Param("locked") boolean locked,
                                          @Param("offset") int offset,
                                          @Param("pageSize") int pageSize);

    Map<String, Object> selectUser(@Param("userId") String userId);

    int updateAccountStatus(@Param("userId") String userId, @Param("status") String status);

    int updateRole(@Param("userId") String userId, @Param("role") String role);

    int unlockUser(@Param("userId") String userId);

    int countActiveSuperAdmins();

    Map<String, Object> selectContentSummary();

    List<Map<String, Object>> selectGroups(@Param("query") String query,
                                           @Param("offset") int offset,
                                           @Param("pageSize") int pageSize);

    List<Map<String, Object>> selectPhotos(@Param("query") String query,
                                           @Param("offset") int offset,
                                           @Param("pageSize") int pageSize);

    List<Map<String, Object>> selectMemos(@Param("query") String query,
                                          @Param("offset") int offset,
                                          @Param("pageSize") int pageSize);

    int setGroupVisibility(@Param("groupId") long groupId, @Param("visibility") String visibility);

    int deleteGroup(@Param("groupId") long groupId);

    int setPhotoVisibility(@Param("imageId") long imageId, @Param("visibility") String visibility);

    int deletePhoto(@Param("imageId") long imageId);

    int setMemoVisibility(@Param("groupId") long groupId, @Param("visibility") String visibility);

    int deleteMemo(@Param("groupId") long groupId);

    List<Map<String, Object>> selectLinkedFiles();

    List<Map<String, Object>> selectImagePairs();

    Map<String, Object> selectDashboardSummary(@Param("days") int days);

    List<Map<String, Object>> selectErrorCounts(@Param("days") int days);

    List<Map<String, Object>> selectErrorDetails(@Param("code") String code, @Param("days") int days);

    List<Map<String, Object>> selectDefects();

    int updateDefectStatus(@Param("defectId") String defectId, @Param("status") String status);

    void insertAudit(@Param("actorId") String actorId,
                     @Param("action") String action,
                     @Param("targetType") String targetType,
                     @Param("targetId") String targetId,
                     @Param("details") String details);

    void insertError(@Param("code") String code, @Param("domain") String domain, @Param("message") String message);
}
