package com.map.egis.festival.mapper;

import com.map.egis.festival.dto.ImportHistoryDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ImportHistoryMapper {
    int insert(@Param("fileName") String fileName,
               @Param("totalRows") int totalRows,
               @Param("successRows") int successRows,
               @Param("skippedRows") int skippedRows);

    List<ImportHistoryDto> findRecent();
}
