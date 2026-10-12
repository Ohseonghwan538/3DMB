package com.map.egis.festival.dto;

import java.time.LocalDate;

public record FestivalDetailDto(long id, String name, String venue, String roadAddr,
                                LocalDate startDate, LocalDate endDate, String content,
                                String hostOrg, String mngOrg, double lat, double lng,
                                String typeCode, String typeName) {
}
