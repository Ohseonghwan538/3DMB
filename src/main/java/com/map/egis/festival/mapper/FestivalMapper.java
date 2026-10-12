package com.map.egis.festival.mapper;

import com.map.egis.festival.dto.FestivalDetailDto;
import com.map.egis.festival.dto.FestivalMarkerDto;
import com.map.egis.festival.dto.FestivalSearchCond;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface FestivalMapper {
    List<FestivalMarkerDto> findMarkers(FestivalSearchCond condition);

    FestivalDetailDto findById(@Param("id") long id);

    int upsert(@Param("name") String name,
               @Param("venue") String venue,
               @Param("startDate") LocalDate startDate,
               @Param("endDate") LocalDate endDate,
               @Param("content") String content,
               @Param("hostOrg") String hostOrg,
               @Param("mngOrg") String mngOrg,
               @Param("roadAddr") String roadAddr,
               @Param("lotAddr") String lotAddr,
               @Param("sido") String sido,
               @Param("sigungu") String sigungu,
               @Param("latitude") BigDecimal latitude,
               @Param("longitude") BigDecimal longitude,
               @Param("typeCode") String typeCode);
}
