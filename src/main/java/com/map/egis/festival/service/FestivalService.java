package com.map.egis.festival.service;

import com.map.egis.festival.dto.FestivalDetailDto;
import com.map.egis.festival.dto.FestivalMarkerDto;
import com.map.egis.festival.dto.FestivalSearchCond;
import com.map.egis.festival.dto.FestivalTypeDto;
import com.map.egis.festival.mapper.FestivalMapper;
import com.map.egis.festival.mapper.FestivalTypeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Service
public class FestivalService {
    private final FestivalMapper festivalMapper;
    private final FestivalTypeMapper festivalTypeMapper;

    public FestivalService(FestivalMapper festivalMapper, FestivalTypeMapper festivalTypeMapper) {
        this.festivalMapper = festivalMapper;
        this.festivalTypeMapper = festivalTypeMapper;
    }

    @Transactional(readOnly = true)
    public FestivalList find(FestivalSearchCond condition) {
        List<FestivalMarkerDto> items = festivalMapper.findMarkers(condition);
        return new FestivalList(items.size(), items);
    }

    @Transactional(readOnly = true)
    public FestivalDetailDto detail(long id) {
        return festivalMapper.findById(id);
    }

    @Transactional(readOnly = true)
    public List<FestivalTypeDto> types() {
        return festivalTypeMapper.findAll();
    }

    @Transactional(readOnly = true)
    public List<String> regions() {
        return festivalTypeMapper.findRegions();
    }

    public FestivalSearchCond validate(String keyword, String startDate, String endDate,
                                       String sido, String types) {
        var from = parseDate(startDate, "startDate");
        var to = parseDate(endDate, "endDate");
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("startDate는 endDate보다 늦을 수 없습니다.");
        }
        List<String> typeCodes = types == null || types.isBlank()
                ? List.of()
                : Arrays.stream(types.split(",", -1)).map(String::trim).distinct().toList();
        if (typeCodes.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("유형 코드가 올바르지 않습니다.");
        }
        Set<String> validCodes = festivalTypeMapper.findAll().stream()
                .map(FestivalTypeDto::typeCode).collect(java.util.stream.Collectors.toSet());
        if (typeCodes.stream().anyMatch(code -> !validCodes.contains(code))) {
            throw new IllegalArgumentException("알 수 없는 유형 코드입니다.");
        }
        return new FestivalSearchCond(blankToNull(keyword), from, to, blankToNull(sido), typeCodes);
    }

    private java.time.LocalDate parseDate(String value, String name) {
        if (value == null || value.isBlank()) return null;
        try {
            return java.time.LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException invalidDate) {
            throw new IllegalArgumentException(name + "는 yyyy-MM-dd 형식이어야 합니다.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record FestivalList(int count, List<FestivalMarkerDto> items) { }
}
