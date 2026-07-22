package br.com.agendaplatform.reporting.api;

import br.com.agendaplatform.reporting.application.DashboardService;
import br.com.agendaplatform.reporting.application.DashboardSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
class DashboardController {

    private final DashboardService dashboardService;

    DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    DashboardSummary today() {
        return dashboardService.today();
    }
}
