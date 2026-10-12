package com.map.egis.festival.dto;

import java.time.LocalDate;

public record FestivalMarkerDto(long id, String name, double lat, double lng,
                                String typeCode, String iconUrl,
                                LocalDate startDate, LocalDate endDate) {
}
