package com.bugtrace.controller;

import com.bugtrace.dto.response.DashboardStatsResponse;
import com.bugtrace.service.DashboardService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Validated
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    public DashboardStatsResponse stats(
            @RequestParam(defaultValue = "14") @Min(1) @Max(90) int days) {
        return dashboardService.getStats(days);
    }
}
