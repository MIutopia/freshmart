package com.freshmart.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    @GetMapping("/info")
    public Map<String, String> info() {
        return Map.of("name", "FreshMart", "version", "0.1.0", "status", "scaffold");
    }
}
