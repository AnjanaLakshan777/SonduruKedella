package com.sondurukedella.employees.controller;

import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.employees.dto.EmployeeCreateRequest;
import com.sondurukedella.employees.dto.EmployeeResponse;
import com.sondurukedella.employees.dto.EmployeeStatusRequest;
import com.sondurukedella.employees.dto.EmployeeUpdateRequest;
import com.sondurukedella.employees.entity.EmployeeStatus;
import com.sondurukedella.employees.service.EmployeeService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

// No DELETE: workers are deactivated (PUT /{id}/status), because their days and paid weeks stay.
@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService service;

    // q searches name, role and phone; status=ACTIVE for the pick lists
    @GetMapping
    public PageResponse<EmployeeResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) EmployeeStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(q, role, status, pageable);
    }

    @GetMapping("/{id}")
    public EmployeeResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody EmployeeCreateRequest request) {
        EmployeeResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public EmployeeResponse update(@PathVariable long id, @Valid @RequestBody EmployeeUpdateRequest request) {
        return service.update(id, request);
    }

    @PutMapping("/{id}/status")
    public EmployeeResponse setStatus(@PathVariable long id, @Valid @RequestBody EmployeeStatusRequest request) {
        return service.setStatus(id, request);
    }
}
