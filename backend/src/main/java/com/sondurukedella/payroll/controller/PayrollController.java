package com.sondurukedella.payroll.controller;

import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.payroll.dto.EmployeePayrollHistory;
import com.sondurukedella.payroll.dto.PayrollAdjustmentRequest;
import com.sondurukedella.payroll.dto.PayrollCorrectionRequest;
import com.sondurukedella.payroll.dto.PayrollCorrectionResponse;
import com.sondurukedella.payroll.dto.PayrollLineDetail;
import com.sondurukedella.payroll.dto.PayrollPaymentRequest;
import com.sondurukedella.payroll.dto.PayrollPaymentResponse;
import com.sondurukedella.payroll.dto.PayrollWeekResponse;
import com.sondurukedella.payroll.dto.PayrollWeekSummary;
import com.sondurukedella.payroll.dto.ProjectLabourResponse;
import com.sondurukedella.payroll.dto.SalaryAdvanceRequest;
import com.sondurukedella.payroll.dto.SalaryAdvanceResponse;
import com.sondurukedella.payroll.dto.VoidRequest;
import com.sondurukedella.payroll.service.PayrollService;
import com.sondurukedella.payroll.service.PayrollService.PaymentResult;
import com.sondurukedella.payroll.service.SalaryAdvanceService;
import com.sondurukedella.payroll.service.SalaryAdvanceService.AdvanceResult;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Weekly payroll. A week is addressed by its Monday. Nothing here is deleted: payments and advances are
// voided, a paid week is corrected. 201 for a new payment or advance, 200 when the same requestId was
// already posted.
@RestController
@RequestMapping("/api/v1/payroll")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payroll;
    private final SalaryAdvanceService advances;

    @GetMapping("/weeks")
    public List<PayrollWeekSummary> weeks(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return payroll.weeks(from, to);
    }

    @GetMapping("/weeks/{weekStart}")
    public PayrollWeekResponse week(@PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate weekStart) {
        return payroll.week(weekStart);
    }

    @GetMapping("/weeks/{weekStart}/employees/{employeeId}")
    public PayrollLineDetail line(@PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate weekStart, @PathVariable long employeeId) {
        return payroll.line(weekStart, employeeId);
    }

    @PutMapping("/weeks/{weekStart}/employees/{employeeId}/adjustment")
    public PayrollLineDetail adjust(@PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate weekStart, @PathVariable long employeeId,
            @Valid @RequestBody PayrollAdjustmentRequest request) {
        return payroll.setAdjustment(weekStart, employeeId, request);
    }

    @PostMapping("/weeks/{weekStart}/employees/{employeeId}/payments")
    public ResponseEntity<PayrollPaymentResponse> pay(@PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate weekStart,
            @PathVariable long employeeId, @Valid @RequestBody PayrollPaymentRequest request) {
        PaymentResult result = payroll.recordPayment(weekStart, employeeId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.payment());
    }

    @PostMapping("/weeks/{weekStart}/employees/{employeeId}/payments/{paymentId}/void")
    public PayrollPaymentResponse voidPayment(@PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate weekStart,
            @PathVariable long employeeId, @PathVariable long paymentId, @Valid @RequestBody VoidRequest request) {
        return payroll.voidPayment(weekStart, employeeId, paymentId, request);
    }

    // only for a week whose advance deduction takes all its pay (nothing to pay, but the recovery must be kept)
    @PostMapping("/weeks/{weekStart}/employees/{employeeId}/close")
    public PayrollLineDetail close(@PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate weekStart, @PathVariable long employeeId) {
        return payroll.close(weekStart, employeeId);
    }

    // 201 for a new correction, 200 when the same requestId was already recorded
    @PostMapping("/weeks/{weekStart}/employees/{employeeId}/corrections")
    public ResponseEntity<PayrollCorrectionResponse> correct(@PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate weekStart,
            @PathVariable long employeeId, @Valid @RequestBody PayrollCorrectionRequest request) {
        PayrollService.CorrectionResult result = payroll.correct(weekStart, employeeId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.correction());
    }

    // a wrong correction is voided (kept, with the reason) and a new one recorded
    @PostMapping("/weeks/{weekStart}/employees/{employeeId}/corrections/{correctionId}/void")
    public PayrollCorrectionResponse voidCorrection(@PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate weekStart,
            @PathVariable long employeeId, @PathVariable long correctionId, @Valid @RequestBody VoidRequest request) {
        return payroll.voidCorrection(weekStart, employeeId, correctionId, request);
    }

    // from/to are Mondays; by default the last 12 weeks
    @GetMapping("/employees/{employeeId}/history")
    public EmployeePayrollHistory history(@PathVariable long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return payroll.history(employeeId, from, to);
    }

    @GetMapping("/projects/{projectId}/labour")
    public ProjectLabourResponse projectLabour(@PathVariable long projectId) {
        return payroll.projectLabour(projectId);
    }

    @GetMapping("/advances")
    public PageResponse<SalaryAdvanceResponse> listAdvances(@RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return advances.search(employeeId, from, to, pageable);
    }

    @PostMapping("/advances")
    public ResponseEntity<SalaryAdvanceResponse> recordAdvance(@Valid @RequestBody SalaryAdvanceRequest request) {
        AdvanceResult result = advances.record(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.advance());
    }

    @PostMapping("/advances/{id}/void")
    public SalaryAdvanceResponse voidAdvance(@PathVariable long id, @Valid @RequestBody VoidRequest request) {
        return advances.voidAdvance(id, request);
    }
}
