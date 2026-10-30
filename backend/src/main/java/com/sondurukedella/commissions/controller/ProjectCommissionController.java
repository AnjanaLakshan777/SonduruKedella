package com.sondurukedella.commissions.controller;

import com.sondurukedella.commissions.dto.CommissionSetRequest;
import com.sondurukedella.commissions.dto.ProjectCommissionsResponse;
import com.sondurukedella.commissions.service.CommissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Sits in commissions, not projects, so projects doesn't have to depend on this module.
@RestController
@RequestMapping("/api/v1/projects/{projectId}/commissions")
@RequiredArgsConstructor
public class ProjectCommissionController {

    private final CommissionService service;

    @GetMapping
    public ProjectCommissionsResponse get(@PathVariable long projectId) {
        return service.forProject(projectId);
    }

    @PutMapping
    public ProjectCommissionsResponse save(@PathVariable long projectId, @Valid @RequestBody CommissionSetRequest request) {
        return service.saveSet(projectId, request);
    }
}
