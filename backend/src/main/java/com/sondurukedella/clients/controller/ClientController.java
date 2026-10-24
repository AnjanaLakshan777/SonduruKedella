package com.sondurukedella.clients.controller;

import com.sondurukedella.clients.dto.ClientCreateRequest;
import com.sondurukedella.clients.dto.ClientResponse;
import com.sondurukedella.clients.dto.ClientUpdateRequest;
import com.sondurukedella.clients.entity.ClientKind;
import com.sondurukedella.clients.service.ClientService;
import com.sondurukedella.common.web.PageResponse;
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

@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService service;

    // q searches name, city and phone, like the search box on the clients page.
    // e.g. /api/v1/clients?q=kekirawa&kind=BUSINESS&page=0&size=20&sort=name,asc
    @GetMapping
    public PageResponse<ClientResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ClientKind kind,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(q, kind, pageable);
    }

    @GetMapping("/{id}")
    public ClientResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<ClientResponse> create(@Valid @RequestBody ClientCreateRequest request) {
        ClientResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ClientResponse update(@PathVariable long id, @Valid @RequestBody ClientUpdateRequest request) {
        return service.update(id, request);
    }
}
