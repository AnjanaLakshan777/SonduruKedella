package com.sondurukedella.expenses.controller;

import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.expenses.dto.ExpenseCreateRequest;
import com.sondurukedella.expenses.dto.ExpenseDetail;
import com.sondurukedella.expenses.dto.ExpenseFilter;
import com.sondurukedella.expenses.dto.ExpensePaymentRequest;
import com.sondurukedella.expenses.dto.ExpensePaymentResponse;
import com.sondurukedella.expenses.dto.ExpensePaymentStatus;
import com.sondurukedella.expenses.dto.ExpensePaymentVoidRequest;
import com.sondurukedella.expenses.dto.ExpenseResponse;
import com.sondurukedella.expenses.dto.ExpenseSummaryResponse;
import com.sondurukedella.expenses.dto.ExpenseUpdateRequest;
import com.sondurukedella.expenses.entity.ExpenseScope;
import com.sondurukedella.expenses.service.ExpenseAttachmentService;
import com.sondurukedella.expenses.service.ExpensePaymentService;
import com.sondurukedella.expenses.service.ExpensePaymentService.PaymentResult;
import com.sondurukedella.expenses.service.ExpenseService;
import com.sondurukedella.settings.entity.ExpenseGroup;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.multipart.MultipartFile;

// One place for project and company expenses; a project's expenses are just ?projectId=. No DELETE:
// a wrong expense is corrected by editing it, and a wrong payment by voiding it.
@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService service;
    private final ExpensePaymentService paymentService;
    private final ExpenseAttachmentService attachments;

    // e.g. ?scope=PROJECT&projectId=4&status=OVERDUE&from=2026-10-01&to=2026-10-31, or ?supplierId=7 for one supplier
    @GetMapping
    public PageResponse<ExpenseResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ExpenseScope scope,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) ExpenseGroup group,
            @RequestParam(required = false) ExpensePaymentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(new ExpenseFilter(q, scope, projectId, categoryId, supplierId, group, status, from, to), pageable);
    }

    // the KPI strip, for the same filters as the list
    @GetMapping("/summary")
    public ExpenseSummaryResponse summary(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ExpenseScope scope,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) ExpenseGroup group,
            @RequestParam(required = false) ExpensePaymentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.summary(new ExpenseFilter(q, scope, projectId, categoryId, supplierId, group, status, from, to));
    }

    @GetMapping("/{id}")
    public ExpenseDetail get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> create(@Valid @RequestBody ExpenseCreateRequest request) {
        ExpenseResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ExpenseResponse update(@PathVariable long id, @Valid @RequestBody ExpenseUpdateRequest request) {
        return service.update(id, request);
    }

    @GetMapping("/payments")
    public List<ExpensePaymentResponse> allPayments() {
        return paymentService.listAll();
    }

    // the receipt or bill as a PDF in a multipart part called "file"; replaces the old one
    @PutMapping(path = "/{id}/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExpenseDetail attach(@PathVariable long id, @RequestPart("file") MultipartFile file) {
        attachments.attach(id, file);
        return service.get(id);
    }

    // always a download, never shown inline (same as agreements)
    @GetMapping("/{id}/attachment/download")
    public ResponseEntity<InputStreamResource> attachment(@PathVariable long id) {
        ExpenseAttachmentService.AttachmentDownload download = attachments.attachment(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(download.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(download.content()));
    }

    @GetMapping("/{id}/payments")
    public List<ExpensePaymentResponse> payments(@PathVariable long id) {
        return paymentService.list(id);
    }

    // 201 for a new payment, 200 when the same requestId was already recorded (nothing new saved)
    @PostMapping("/{id}/payments")
    public ResponseEntity<ExpensePaymentResponse> pay(@PathVariable long id, @Valid @RequestBody ExpensePaymentRequest request) {
        PaymentResult result = paymentService.record(id, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.payment());
    }

    @PostMapping("/{id}/payments/{paymentId}/void")
    public ExpensePaymentResponse voidPayment(@PathVariable long id, @PathVariable long paymentId,
            @Valid @RequestBody ExpensePaymentVoidRequest request) {
        return paymentService.voidPayment(id, paymentId, request);
    }
}
