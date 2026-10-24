package com.sondurukedella.revenue.controller;

import com.sondurukedella.revenue.dto.ReceivablesSummaryResponse;
import com.sondurukedella.revenue.service.ReceivableService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Company-wide figures only. Anything for one project lives under /api/v1/projects/{id}/..., in the
// projects module, which checks the project exists before calling into Revenue.
@RestController
@RequestMapping("/api/v1/receivables")
@RequiredArgsConstructor
public class ReceivablesController {

    private final ReceivableService service;

    @GetMapping("/summary")
    public ReceivablesSummaryResponse summary() {
        return service.summary();
    }
}
