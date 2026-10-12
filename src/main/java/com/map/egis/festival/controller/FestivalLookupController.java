package com.map.egis.festival.controller;

import com.map.egis.festival.service.FestivalService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
public class FestivalLookupController {
    private final FestivalService festivalService;

    public FestivalLookupController(FestivalService festivalService) {
        this.festivalService = festivalService;
    }

    @GetMapping("/api/festival-types")
    public ResponseEntity<?> types() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(festivalService.types());
    }

    @GetMapping("/api/regions")
    public ResponseEntity<?> regions() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(festivalService.regions());
    }
}
