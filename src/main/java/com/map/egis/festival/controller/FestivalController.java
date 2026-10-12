package com.map.egis.festival.controller;

import com.map.egis.festival.dto.FestivalDetailDto;
import com.map.egis.festival.dto.FestivalSearchCond;
import com.map.egis.festival.service.FestivalService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/festivals")
public class FestivalController {
    private final FestivalService festivalService;

    public FestivalController(FestivalService festivalService) {
        this.festivalService = festivalService;
    }

    @GetMapping
    public FestivalService.FestivalList list(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String startDate,
                                              @RequestParam(required = false) String endDate,
                                              @RequestParam(required = false) String sido,
                                              @RequestParam(required = false) String types) {
        FestivalSearchCond condition = festivalService.validate(keyword, startDate, endDate, sido, types);
        return festivalService.find(condition);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable long id) {
        if (id <= 0) throw new IllegalArgumentException("축제 id가 올바르지 않습니다.");
        FestivalDetailDto detail = festivalService.detail(id);
        if (detail == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "code", "FESTIVAL_NOT_FOUND", "message", "축제 정보를 찾을 수 없습니다."));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(detail);
    }
}
