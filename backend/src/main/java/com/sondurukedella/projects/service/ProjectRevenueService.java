package com.sondurukedella.projects.service;

import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.projects.entity.Project;
import com.sondurukedella.projects.repository.ProjectRepository;
import com.sondurukedella.revenue.dto.BillInstallmentRequest;
import com.sondurukedella.revenue.dto.DeductionApplyRequest;
import com.sondurukedella.revenue.dto.DeductionReverseRequest;
import com.sondurukedella.revenue.dto.DelayCalculationResponse;
import com.sondurukedella.revenue.dto.DelayTermsResponse;
import com.sondurukedella.revenue.dto.InstallmentReceivableDetail;
import com.sondurukedella.revenue.dto.InstallmentReceivableResponse;
import com.sondurukedella.revenue.dto.ProjectFacts;
import com.sondurukedella.revenue.dto.ProjectReceivablesResponse;
import com.sondurukedella.revenue.dto.ReceiptCreateRequest;
import com.sondurukedella.revenue.dto.ReceiptResponse;
import com.sondurukedella.revenue.dto.ReceiptVoidRequest;
import com.sondurukedella.revenue.dto.WriteOffCreateRequest;
import com.sondurukedella.revenue.dto.WriteOffResponse;
import com.sondurukedella.revenue.dto.WriteOffReversalRequest;
import com.sondurukedella.revenue.dto.InstallmentResponse;
import com.sondurukedella.revenue.dto.ScheduleRevisionRequest;
import com.sondurukedella.revenue.dto.VariationRequest;
import com.sondurukedella.revenue.service.InstallmentScheduleService;
import com.sondurukedella.revenue.service.BillingService;
import com.sondurukedella.revenue.service.DelayDeductionService;
import com.sondurukedella.revenue.service.ReceiptService;
import com.sondurukedella.revenue.service.ReceiptService.ReceiptResult;
import com.sondurukedella.revenue.service.ReceivableService;
import com.sondurukedella.revenue.service.WriteOffService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// The projects side of the money endpoints: checks the project exists (so an unknown one is a 404,
// not an empty result) and hands Revenue the project facts it needs. Revenue does the work in its
// own transactions and never reaches back into this module.
@Service
@RequiredArgsConstructor
public class ProjectRevenueService {

    private final ProjectRepository repository;
    private final ReceivableService receivables;
    private final BillingService billing;
    private final ReceiptService receipts;
    private final WriteOffService writeOffs;
    private final DelayDeductionService deductions;
    private final InstallmentScheduleService schedule;

    public ProjectReceivablesResponse receivables(long projectId) {
        requireExists(projectId);
        return receivables.forProject(projectId);
    }

    public InstallmentReceivableDetail installment(long projectId, long installmentId) {
        requireExists(projectId);
        return receivables.installment(projectId, installmentId);
    }

    public InstallmentReceivableResponse bill(long projectId, long installmentId, BillInstallmentRequest request) {
        requireExists(projectId);
        return billing.bill(projectId, installmentId, request);
    }

    public ReceiptResult recordReceipt(long projectId, long installmentId, ReceiptCreateRequest request) {
        requireExists(projectId);
        return receipts.record(projectId, installmentId, request);
    }

    public List<ReceiptResponse> receipts(long projectId) {
        requireExists(projectId);
        return receivables.receiptsForProject(projectId);
    }

    public ReceiptResponse receipt(long projectId, long receiptId) {
        requireExists(projectId);
        return receipts.get(projectId, receiptId);
    }

    public ReceiptResponse voidReceipt(long projectId, long receiptId, ReceiptVoidRequest request) {
        requireExists(projectId);
        return receipts.voidReceipt(projectId, receiptId, request);
    }

    public WriteOffResponse writeOff(long projectId, long installmentId, WriteOffCreateRequest request) {
        requireExists(projectId);
        return writeOffs.create(projectId, installmentId, request);
    }

    public WriteOffResponse reverseWriteOff(long projectId, long writeOffId, WriteOffReversalRequest request) {
        requireExists(projectId);
        return writeOffs.reverse(projectId, writeOffId, request);
    }

    // take the contract value from the project, not the request
    public List<InstallmentResponse> reviseSchedule(long projectId, ScheduleRevisionRequest request) {
        return schedule.revise(projectId, facts(projectId).contractValue(), request);
    }

    public InstallmentResponse addVariation(long projectId, VariationRequest request) {
        requireExists(projectId);
        return schedule.addVariation(projectId, request);
    }

    public DelayCalculationResponse delayCalculation(long projectId, LocalDate completionDate) {
        return deductions.calculate(facts(projectId), completionDate);
    }

    public DelayTermsResponse applyDeduction(long projectId, DeductionApplyRequest request) {
        return deductions.apply(facts(projectId), request);
    }

    public DelayTermsResponse reverseDeduction(long projectId, DeductionReverseRequest request) {
        return deductions.reverse(facts(projectId), request);
    }

    private void requireExists(long projectId) {
        if (!repository.existsById(projectId)) {
            throw new NotFoundException("Project not found.");
        }
    }

    // Contract value and type are fixed after creation, so reading them here outside Revenue's
    // transaction is safe. The completion date is only a default the caller can override.
    private ProjectFacts facts(long projectId) {
        Project project = repository.findById(projectId).orElseThrow(() -> new NotFoundException("Project not found."));
        return new ProjectFacts(project.getId(), project.getType().name(), project.getContractValue(),
                project.getActualCompletion());
    }
}
