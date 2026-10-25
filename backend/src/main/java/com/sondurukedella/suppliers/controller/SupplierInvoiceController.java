package com.sondurukedella.suppliers.controller;

import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.suppliers.dto.SupplierInvoiceCreateRequest;
import com.sondurukedella.suppliers.dto.SupplierInvoiceDetail;
import com.sondurukedella.suppliers.dto.SupplierInvoiceFilter;
import com.sondurukedella.suppliers.dto.SupplierInvoiceResponse;
import com.sondurukedella.suppliers.dto.SupplierInvoiceResponse.InvoiceStatus;
import com.sondurukedella.suppliers.dto.SupplierInvoiceSummary;
import com.sondurukedella.suppliers.dto.SupplierPaymentRequest;
import com.sondurukedella.suppliers.dto.SupplierPaymentResponse;
import com.sondurukedella.suppliers.dto.SupplierPaymentVoidRequest;
import com.sondurukedella.suppliers.entity.InvoiceAllocation;
import com.sondurukedella.suppliers.service.SupplierInvoiceService;
import com.sondurukedella.suppliers.service.SupplierInvoiceService.PaymentResult;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// Supplier invoices and payables. No edit or delete: an invoice is a record of what the supplier
// billed (and a STOCK invoice is already in the stock ledger); payments are voided, not deleted.
@RestController
@RequestMapping("/api/v1/supplier-invoices")
@RequiredArgsConstructor
public class SupplierInvoiceController {

    private final SupplierInvoiceService service;

    @GetMapping
    public PageResponse<SupplierInvoiceResponse> list(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) InvoiceAllocation allocation,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(new SupplierInvoiceFilter(supplierId, projectId, allocation, status, from, to), pageable);
    }

    // the Payables KPIs, for the same filters
    @GetMapping("/summary")
    public SupplierInvoiceSummary summary(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) InvoiceAllocation allocation,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.summary(new SupplierInvoiceFilter(supplierId, projectId, allocation, status, from, to));
    }

    @GetMapping("/{id}")
    public SupplierInvoiceDetail get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<SupplierInvoiceDetail> create(@Valid @RequestBody SupplierInvoiceCreateRequest request) {
        SupplierInvoiceDetail created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.invoice().id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}/payments")
    public List<SupplierPaymentResponse> payments(@PathVariable long id) {
        return service.listPayments(id);
    }

    // 201 for a new payment, 200 when the same requestId was already recorded
    @PostMapping("/{id}/payments")
    public ResponseEntity<SupplierPaymentResponse> pay(@PathVariable long id, @Valid @RequestBody SupplierPaymentRequest request) {
        PaymentResult result = service.recordPayment(id, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.payment());
    }

    @PostMapping("/{id}/payments/{paymentId}/void")
    public SupplierPaymentResponse voidPayment(@PathVariable long id, @PathVariable long paymentId,
            @Valid @RequestBody SupplierPaymentVoidRequest request) {
        return service.voidPayment(id, paymentId, request);
    }
}
