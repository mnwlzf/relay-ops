package com.relayops.monitoring.adapter.in.web;

import com.relayops.monitoring.application.usage.MonitoringService;
import com.relayops.monitoring.application.usage.UsageReadModel.AbnormalAccount;
import com.relayops.monitoring.application.usage.UsageReadModel.Overview;
import com.relayops.monitoring.application.usage.UsageReadModel.TrendPoint;
import com.relayops.shared.web.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Monitoring read API: overview, abnormal accounts and per-account trend. */
@RestController
@RequestMapping("/api/v1/monitoring")
public class MonitoringController {

    private final MonitoringService monitoring;

    public MonitoringController(MonitoringService monitoring) {
        this.monitoring = monitoring;
    }

    @GetMapping("/overview")
    public ApiResponse<Overview> overview() {
        return ApiResponse.of(monitoring.overview());
    }

    @GetMapping("/abnormal")
    public ApiResponse<List<AbnormalAccount>> abnormal() {
        return ApiResponse.of(monitoring.abnormal());
    }

    @GetMapping("/accounts/{id}/trend")
    public ApiResponse<List<TrendPoint>> trend(@PathVariable long id,
                                               @RequestParam(defaultValue = "24") int hours) {
        return ApiResponse.of(monitoring.trend(id, hours));
    }
}
