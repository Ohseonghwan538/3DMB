package com.map.egis.festival.service;

import com.map.egis.festival.dto.FestivalTypeDto;
import com.map.egis.festival.mapper.FestivalMapper;
import com.map.egis.festival.mapper.FestivalTypeMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FestivalServiceTests {
    @Test
    void validatesDatesAndKnownMultipleTypes() {
        FestivalTypeMapper typeMapper = mock(FestivalTypeMapper.class);
        when(typeMapper.findAll()).thenReturn(List.of(
                new FestivalTypeDto("MUSIC", "음악", "/img/marker/MUSIC.png"),
                new FestivalTypeDto("FOOD", "음식", "/img/marker/FOOD.png")));
        FestivalService service = new FestivalService(mock(FestivalMapper.class), typeMapper);

        var condition = service.validate(" 재즈 ", "2026-05-01", "2026-06-01", "서울특별시", "MUSIC,FOOD");

        assertEquals("재즈", condition.keyword());
        assertEquals(List.of("MUSIC", "FOOD"), condition.types());
        assertThrows(IllegalArgumentException.class,
                () -> service.validate(null, "2026-06-01", "2026-05-01", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.validate(null, null, null, null, "UNKNOWN"));
    }
}
