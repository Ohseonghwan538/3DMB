package com.map.egis.festival.mapper;

import com.map.egis.festival.dto.FestivalTypeDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FestivalTypeMapper {
    List<FestivalTypeDto> findAll();

    List<String> findRegions();
}
