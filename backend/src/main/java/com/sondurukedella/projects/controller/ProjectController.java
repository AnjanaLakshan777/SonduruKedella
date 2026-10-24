package com.sondurukedella.projects.controller;

import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.projects.dto.ProjectCreateRequest;
import com.sondurukedella.projects.dto.ProjectFilter;
import com.sondurukedella.projects.dto.ProjectResponse;
import com.sondurukedella.projects.dto.ProjectStatusUpdateRequest;
import com.sondurukedella.projects.entity.ProjectStatus;
import com.sondurukedella.projects.entity.ProjectType;
import com.sondurukedella.projects.service.ProjectService;
import com.sondurukedella.revenue.dto.DelayTermsResponse;
import com.sondurukedella.revenue.dto.InstallmentResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// No DELETE and no general PUT/PATCH: the UI only creates projects and changes their status.
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService service;

    // q searches name, code, location and client name, like the search box on the projects page.
    // e.g. /api/v1/projects?q=kekirawa&type=CONSTRUCTION&dueFrom=2027-01-01&dueTo=2027-12-31
    @GetMapping
    public PageResponse<ProjectResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ProjectType type,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @PageableDefault(size = 10) Pageable pageable) {
        return service.search(new ProjectFilter(q, type, status, clientId, startFrom, startTo, dueFrom, dueTo), pageable);
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable long id) {
        return service.get(id);
    }

    // Separate from GET /{id} because ProjectResponse is also the list row; nesting the schedule
    // there would load it for every project on the page. An empty list means no schedule (no-fee consultation).
    @GetMapping("/{id}/schedule")
    public List<InstallmentResponse> schedule(@PathVariable long id) {
        return service.getSchedule(id);
    }

    // 204 when the project exists but has no terms (always so for design and consultation).
    // Not 404 - that would look the same as a wrong project id.
    @GetMapping("/{id}/delay-terms")
    public ResponseEntity<DelayTermsResponse> delayTerms(@PathVariable long id) {
        return service.getDelayTerms(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectCreateRequest request) {
        ProjectResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}/status")
    public ProjectResponse changeStatus(@PathVariable long id, @Valid @RequestBody ProjectStatusUpdateRequest request) {
        return service.changeStatus(id, request);
    }
}
