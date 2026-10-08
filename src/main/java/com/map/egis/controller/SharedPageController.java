package com.map.egis.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class SharedPageController {
    @GetMapping("/shared/{token}")
    public String sharedPage(@PathVariable String token) {
        if (!token.matches("[A-Za-z0-9_-]{40,50}")) return "redirect:/";
        return "forward:/src/components/shared.html";
    }
}
