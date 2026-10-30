package com.sondurukedella.activity.controller;

import com.sondurukedella.activity.dto.ActivityResponse;
import com.sondurukedella.activity.entity.ActivityKind;
import com.sondurukedella.activity.service.ActivityService;
import com.sondurukedella.common.web.PageResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Recent activity on the dashboard, and the project Activity tab (?projectId=). Read-only - the
// modules write their own entries.
@RestController
@RequestMapping("/api/v1/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService service;

    @GetMapping
    public PageResponse<ActivityResponse> feed(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) ActivityKind kind,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.feed(projectId, kind, from, to, pageable);
    }
}
