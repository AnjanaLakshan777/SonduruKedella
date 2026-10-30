package com.sondurukedella.revenue.controller;

import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.revenue.dto.OtherIncomeCreateRequest;
import com.sondurukedella.revenue.dto.OtherIncomeReceiptResponse;
import com.sondurukedella.revenue.dto.OtherIncomeResponse;
import com.sondurukedella.revenue.dto.OtherIncomeResponse.Status;
import com.sondurukedella.revenue.dto.OtherIncomeSummary;
import com.sondurukedella.revenue.dto.OtherIncomeUpdateRequest;
import com.sondurukedella.revenue.dto.ReceiptCreateRequest;
import com.sondurukedella.revenue.dto.ReceiptVoidRequest;
import com.sondurukedella.revenue.service.OtherIncomeService;
import com.sondurukedella.revenue.service.OtherIncomeService.ReceiptResult;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// Edit any time (not below what's received), delete only if nothing was received. Receipts get voided.
@RestController
@RequestMapping("/api/v1/other-income")
@RequiredArgsConstructor
public class OtherIncomeController {

    private final OtherIncomeService service;

    @GetMapping
    public PageResponse<OtherIncomeResponse> list(
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(status, from, to, pageable);
    }

    @GetMapping("/summary")
    public OtherIncomeSummary summary(
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.summary(status, from, to);
    }

    @GetMapping("/{id}")
    public OtherIncomeResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<OtherIncomeResponse> create(@Valid @RequestBody OtherIncomeCreateRequest request) {
        OtherIncomeResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public OtherIncomeResponse update(@PathVariable long id, @Valid @RequestBody OtherIncomeUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/receipts")
    public List<OtherIncomeReceiptResponse> receipts(@PathVariable long id) {
        return service.listReceipts(id);
    }

    // 201 = new receipt, 200 = same requestId sent again
    @PostMapping("/{id}/receipts")
    public ResponseEntity<OtherIncomeReceiptResponse> receive(@PathVariable long id, @Valid @RequestBody ReceiptCreateRequest request) {
        ReceiptResult result = service.receive(id, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.receipt());
    }

    @PostMapping("/{id}/receipts/{receiptId}/void")
    public OtherIncomeReceiptResponse voidReceipt(@PathVariable long id, @PathVariable long receiptId,
            @Valid @RequestBody ReceiptVoidRequest request) {
        return service.voidReceipt(id, receiptId, request);
    }
}
