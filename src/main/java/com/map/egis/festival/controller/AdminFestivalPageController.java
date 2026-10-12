package com.map.egis.festival.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminFestivalPageController {
    @GetMapping("/admin/festivals")
    public String importPage() {
        return "forward:/admin/festivals.html";
    }
}
