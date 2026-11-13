package com.prideuniversal.domain.dashboard;

import com.prideuniversal.domain.dashboard.DashboardService.DashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Dashboard", description = "The home screen in one call")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/dashboard")
    @Operation(summary = "Society figures (committee), the caller's flat, latest notices, upcoming meetings")
    public DashboardResponse dashboard() {
        return dashboardService.forCaller();
    }
}
