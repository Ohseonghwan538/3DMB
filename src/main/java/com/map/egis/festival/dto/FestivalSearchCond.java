package com.map.egis.festival.dto;

import java.time.LocalDate;
import java.util.List;

public record FestivalSearchCond(String keyword, LocalDate startDate, LocalDate endDate,
                                 String sido, List<String> types) {
}
