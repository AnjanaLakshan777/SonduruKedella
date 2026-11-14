package com.sondurukedella.commissions.controller;

import com.sondurukedella.commissions.dto.CommissionSetRequest;
import com.sondurukedella.commissions.dto.ProjectCommissionsResponse;
import com.sondurukedella.commissions.service.CommissionService;
import com.sondurukedella.security.access.Approvers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
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

    // Anyone with the page can keep drafts; confirming is an approval (Approvers). The flag is in the
    // body, so SecurityConfig can't tell the two apart by path - hence the check here.
    @PutMapping
    public ProjectCommissionsResponse save(@PathVariable long projectId, @Valid @RequestBody CommissionSetRequest request,
            Authentication auth) {
        if (Boolean.TRUE.equals(request.confirm()) && !Approvers.isApprover(auth)) {
            throw new AccessDeniedException("Only an Admin or Super Admin can confirm commissions.");
        }
        return service.saveSet(projectId, request);
    }
}
