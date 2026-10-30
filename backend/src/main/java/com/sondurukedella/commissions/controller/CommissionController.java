package com.sondurukedella.commissions.controller;

import com.sondurukedella.commissions.dto.CommissionDetail;
import com.sondurukedella.commissions.dto.CommissionFilter;
import com.sondurukedella.commissions.dto.CommissionPaymentFilter;
import com.sondurukedella.commissions.dto.CommissionPaymentRequest;
import com.sondurukedella.commissions.dto.CommissionPaymentResponse;
import com.sondurukedella.commissions.dto.CommissionPaymentVoidRequest;
import com.sondurukedella.commissions.dto.CommissionResponse;
import com.sondurukedella.commissions.dto.CommissionState;
import com.sondurukedella.commissions.dto.CommissionSummaryResponse;
import com.sondurukedella.commissions.dto.CommissionVoidRequest;
import com.sondurukedella.commissions.dto.RecipientSummary;
import com.sondurukedella.commissions.service.CommissionPaymentService;
import com.sondurukedella.commissions.service.CommissionPaymentService.PaymentResult;
import com.sondurukedella.commissions.service.CommissionService;
import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.projects.entity.ProjectType;
import jakarta.validation.Valid;
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

// Commissions page endpoints. Arrangements themselves are saved per project (ProjectCommissionController).
// No deletes - confirmed commissions and payments get voided.
@RestController
@RequestMapping("/api/v1/commissions")
@RequiredArgsConstructor
public class CommissionController {

    private final CommissionService service;
    private final CommissionPaymentService paymentService;

    @GetMapping
    public PageResponse<CommissionResponse> list(
            @RequestParam(required = false) String recipient,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) ProjectType type,
            @RequestParam(required = false) CommissionState status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(new CommissionFilter(recipient, q, projectId, type, status, from, to), pageable);
    }

    @GetMapping("/summary")
    public CommissionSummaryResponse summary(@RequestParam(required = false) ProjectType type) {
        return service.summary(type);
    }

    @GetMapping("/recipients")
    public List<RecipientSummary> recipients(@RequestParam(required = false) ProjectType type) {
        return service.recipients(type);
    }

    @GetMapping("/payments")
    public PageResponse<CommissionPaymentResponse> allPayments(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long commissionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Boolean includeVoided,
            @PageableDefault(size = 20) Pageable pageable) {
        return paymentService.search(new CommissionPaymentFilter(projectId, commissionId, from, to, includeVoided), pageable);
    }

    @GetMapping("/{id}")
    public CommissionDetail get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/void")
    public CommissionResponse voidCommission(@PathVariable long id, @Valid @RequestBody CommissionVoidRequest request) {
        return service.voidArrangement(id, request);
    }

    @GetMapping("/{id}/payments")
    public List<CommissionPaymentResponse> payments(@PathVariable long id) {
        return paymentService.list(id);
    }

    // 201 = new, 200 = same requestId sent again
    @PostMapping("/{id}/payments")
    public ResponseEntity<CommissionPaymentResponse> pay(@PathVariable long id, @Valid @RequestBody CommissionPaymentRequest request) {
        PaymentResult result = paymentService.record(id, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.payment());
    }

    @PostMapping("/{id}/payments/{paymentId}/void")
    public CommissionPaymentResponse voidPayment(@PathVariable long id, @PathVariable long paymentId,
            @Valid @RequestBody CommissionPaymentVoidRequest request) {
        return paymentService.voidPayment(id, paymentId, request);
    }
}
