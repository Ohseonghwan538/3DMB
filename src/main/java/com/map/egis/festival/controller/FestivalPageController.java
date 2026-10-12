package com.map.egis.festival.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FestivalPageController {
    @GetMapping("/festivals")
    public String mapPage() {
        return "forward:/components/festivals.html";
    }
}
