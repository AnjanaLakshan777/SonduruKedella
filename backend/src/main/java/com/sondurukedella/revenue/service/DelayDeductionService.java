package com.sondurukedella.revenue.service;

import com.sondurukedella.activity.entity.ActivityKind;
import com.sondurukedella.activity.service.ActivityService;
import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.revenue.dto.DeductionApplyRequest;
import com.sondurukedella.revenue.dto.DeductionReverseRequest;
import com.sondurukedella.revenue.dto.DelayCalculationResponse;
import com.sondurukedella.revenue.dto.DelayTermsResponse;
import com.sondurukedella.revenue.dto.ProjectFacts;
import com.sondurukedella.revenue.entity.DelayDeductionTerms;
import com.sondurukedella.revenue.entity.Installment;
import com.sondurukedella.revenue.mapper.RevenueMapper;
import com.sondurukedella.revenue.repository.DelayDeductionTermsRepository;
import com.sondurukedella.revenue.service.DelayDeductionCalculator.Calculation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Applying and reversing the agreed delay deduction (liquidated damages). It reduces what is payable
// on one billed installment; the contract value is never touched, and it is not retention money.
// Project facts (contract value, completion date) come in from the projects module.
@Service
@RequiredArgsConstructor
public class DelayDeductionService {

    private final InstallmentLedger ledger;
    private final DelayDeductionTermsRepository repository;
    private final RevenueMapper mapper;
    private final ActivityService activityService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DelayCalculationResponse calculate(ProjectFacts project, LocalDate completionDate) {
        DelayDeductionTerms terms = repository.findByProjectId(project.projectId()).orElseThrow(DelayDeductionService::noTerms);
        LocalDate completion = completion(project, completionDate);
        return toResponse(terms, completion, DelayDeductionCalculator.calculate(terms, project.contractValue(), completion));
    }

    // Locks the installment, then the terms - the same order receipts use - so a receipt and a
    // deduction on one installment can't both spend the same balance.
    @Transactional
    public DelayTermsResponse apply(ProjectFacts project, DeductionApplyRequest request) {
        Installment installment = ledger.lockInProject(project.projectId(), request.installmentId());
        DelayDeductionTerms terms = repository.findByProjectIdForUpdate(project.projectId())
                .orElseThrow(DelayDeductionService::noTerms);
        if (terms.isApplied()) {
            throw new ConflictException("A delay deduction is already applied. Reverse it before applying another.");
        }
        ConflictException.requireCurrentVersion(request.version(), terms.getVersion());
        if (!terms.isEnabled()) {
            throw new InvalidRequestException("The delay deduction terms are switched off for this project.");
        }
        if (!installment.isBilled()) {
            throw new InvalidRequestException("A delay deduction can only reduce a billed installment.");
        }

        LocalDate completion = completion(project, request.completionDate());
        Calculation calc = DelayDeductionCalculator.calculate(terms, project.contractValue(), completion);
        if (calc.chargeableDays() == 0) {
            throw new InvalidRequestException("Completion on " + completion + " is not late once the "
                    + terms.getGraceDays() + " grace days are allowed, so no deduction applies.");
        }
        BigDecimal amount = request.amount() == null ? calc.finalAmount() : Money.amount(request.amount());
        if (amount.signum() <= 0) {
            throw new InvalidRequestException("The agreed terms work out to no deduction for this completion date.");
        }
        // The stored rule (and its cap) is the ceiling. A smaller agreed figure is allowed, a larger one isn't.
        if (amount.compareTo(calc.finalAmount()) > 0) {
            throw new InvalidRequestException("More than the " + Money.format(calc.finalAmount())
                    + " the agreed terms allow for this completion date.");
        }
        BigDecimal payable = ledger.outstandingBeforeDeduction(installment);
        if (amount.compareTo(payable) > 0) {
            throw new InvalidRequestException("More than the " + Money.format(payable) + " still payable on "
                    + installment.getDescription() + ".");
        }

        terms.applyDeduction(completion, amount, LocalDate.now(clock), installment.getId());
        terms = repository.saveAndFlush(terms);
        activityService.record(project.projectId(), ActivityKind.DEDUCTION, "Delay deduction of " + Money.format(amount)
                + " applied to " + installment.getDescription() + " (" + calc.chargeableDays() + " chargeable day"
                + (calc.chargeableDays() == 1 ? "" : "s") + ")." + note(request.notes()));
        return mapper.toResponse(terms);
    }

    @Transactional
    public DelayTermsResponse reverse(ProjectFacts project, DeductionReverseRequest request) {
        DelayDeductionTerms terms = repository.findByProjectIdForUpdate(project.projectId())
                .orElseThrow(DelayDeductionService::noTerms);
        if (!terms.isApplied()) {
            throw new ConflictException("No delay deduction is applied.");
        }
        ConflictException.requireCurrentVersion(request.version(), terms.getVersion());

        BigDecimal amount = terms.getAppliedAmount();
        terms.reverseDeduction();
        terms = repository.saveAndFlush(terms);
        activityService.record(project.projectId(), ActivityKind.DEDUCTION,
                "Delay deduction of " + Money.format(amount) + " reversed for review." + note(request.reason()));
        return mapper.toResponse(terms);
    }

    // The date the caller gives, else the project's own actual completion. Never guessed (no
    // "today" fallback), and never written back to the project.
    private static LocalDate completion(ProjectFacts project, LocalDate requested) {
        if (requested != null) {
            return requested;
        }
        if (project.actualCompletion() == null) {
            throw new InvalidRequestException("Give the completion date: the project has no actual completion date yet.");
        }
        return project.actualCompletion();
    }

    private static DelayCalculationResponse toResponse(DelayDeductionTerms terms, LocalDate completion, Calculation calc) {
        return new DelayCalculationResponse(completion, terms.getContractDeadline(), calc.daysLate(), terms.getGraceDays(),
                calc.chargeableDays(), calc.calculatedAmount(), calc.maxDeduction(), calc.capped(), calc.finalAmount());
    }

    private static String note(String text) {
        return text == null || text.isBlank() ? "" : " " + text.strip();
    }

    private static NotFoundException noTerms() {
        return new NotFoundException("This project has no delay deduction terms.");
    }
}
