package com.sondurukedella.projects.controller;

import com.sondurukedella.projects.service.ProjectRevenueService;
import com.sondurukedella.revenue.dto.BillInstallmentRequest;
import com.sondurukedella.revenue.dto.DeductionApplyRequest;
import com.sondurukedella.revenue.dto.DeductionReverseRequest;
import com.sondurukedella.revenue.dto.DelayCalculationResponse;
import com.sondurukedella.revenue.dto.DelayTermsResponse;
import com.sondurukedella.revenue.dto.InstallmentReceivableDetail;
import com.sondurukedella.revenue.dto.InstallmentReceivableResponse;
import com.sondurukedella.revenue.dto.ProjectReceivablesResponse;
import com.sondurukedella.revenue.dto.ReceiptCreateRequest;
import com.sondurukedella.revenue.dto.ReceiptResponse;
import com.sondurukedella.revenue.dto.ReceiptVoidRequest;
import com.sondurukedella.revenue.dto.WriteOffCreateRequest;
import com.sondurukedella.revenue.dto.WriteOffResponse;
import com.sondurukedella.revenue.service.ReceiptService.ReceiptResult;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Money on a project's installments. Every change is a POST that records something new (a billing,
// a receipt, a void, a write-off, a deduction or its reversal); nothing is ever edited or deleted.
// GET /{id}/schedule (ProjectController) stays the plain plan; /receivables is the money view of it.
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@RequiredArgsConstructor
public class ProjectRevenueController {

    private final ProjectRevenueService service;

    @GetMapping("/receivables")
    public ProjectReceivablesResponse receivables(@PathVariable long projectId) {
        return service.receivables(projectId);
    }

    @GetMapping("/installments/{installmentId}")
    public InstallmentReceivableDetail installment(@PathVariable long projectId, @PathVariable long installmentId) {
        return service.installment(projectId, installmentId);
    }

    @PostMapping("/installments/{installmentId}/bill")
    public InstallmentReceivableResponse bill(@PathVariable long projectId, @PathVariable long installmentId,
            @Valid @RequestBody BillInstallmentRequest request) {
        return service.bill(projectId, installmentId, request);
    }

    // 201 for a new receipt, 200 when the same requestId was already recorded (nothing new is saved)
    @PostMapping("/installments/{installmentId}/receipts")
    public ResponseEntity<ReceiptResponse> recordReceipt(@PathVariable long projectId, @PathVariable long installmentId,
            @Valid @RequestBody ReceiptCreateRequest request) {
        ReceiptResult result = service.recordReceipt(projectId, installmentId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.receipt());
    }

    @GetMapping("/receipts")
    public List<ReceiptResponse> receipts(@PathVariable long projectId) {
        return service.receipts(projectId);
    }

    @GetMapping("/receipts/{receiptId}")
    public ReceiptResponse receipt(@PathVariable long projectId, @PathVariable long receiptId) {
        return service.receipt(projectId, receiptId);
    }

    @PostMapping("/receipts/{receiptId}/void")
    public ReceiptResponse voidReceipt(@PathVariable long projectId, @PathVariable long receiptId,
            @Valid @RequestBody ReceiptVoidRequest request) {
        return service.voidReceipt(projectId, receiptId, request);
    }

    @PostMapping("/installments/{installmentId}/write-offs")
    @ResponseStatus(HttpStatus.CREATED)
    public WriteOffResponse writeOff(@PathVariable long projectId, @PathVariable long installmentId,
            @Valid @RequestBody WriteOffCreateRequest request) {
        return service.writeOff(projectId, installmentId, request);
    }

    // preview for the deduction card: what the agreed terms give for a completion date
    @GetMapping("/delay-terms/calculation")
    public DelayCalculationResponse delayCalculation(@PathVariable long projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate completionDate) {
        return service.delayCalculation(projectId, completionDate);
    }

    @PostMapping("/delay-terms/deduction")
    public DelayTermsResponse applyDeduction(@PathVariable long projectId, @Valid @RequestBody DeductionApplyRequest request) {
        return service.applyDeduction(projectId, request);
    }

    @PostMapping("/delay-terms/deduction/reverse")
    public DelayTermsResponse reverseDeduction(@PathVariable long projectId,
            @Valid @RequestBody DeductionReverseRequest request) {
        return service.reverseDeduction(projectId, request);
    }
}
