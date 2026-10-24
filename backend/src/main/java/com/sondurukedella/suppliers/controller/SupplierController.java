package com.sondurukedella.suppliers.controller;

import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.suppliers.dto.SupplierCreateRequest;
import com.sondurukedella.suppliers.dto.SupplierResponse;
import com.sondurukedella.suppliers.dto.SupplierStatusRequest;
import com.sondurukedella.suppliers.dto.SupplierUpdateRequest;
import com.sondurukedella.suppliers.service.SupplierService;
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

// No DELETE: deactivate with PUT /{id}/status instead. A supplier's expenses are
// GET /api/v1/expenses?supplierId={id}, so there's no separate endpoint for them here.
@RestController
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService service;

    // q searches name, contact person, phone and supplies; active=true for the pick list on forms
    @GetMapping
    public PageResponse<SupplierResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(q, active, pageable);
    }

    @GetMapping("/{id}")
    public SupplierResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<SupplierResponse> create(@Valid @RequestBody SupplierCreateRequest request) {
        SupplierResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public SupplierResponse update(@PathVariable long id, @Valid @RequestBody SupplierUpdateRequest request) {
        return service.update(id, request);
    }

    @PutMapping("/{id}/status")
    public SupplierResponse setStatus(@PathVariable long id, @Valid @RequestBody SupplierStatusRequest request) {
        return service.setActive(id, request);
    }
}
