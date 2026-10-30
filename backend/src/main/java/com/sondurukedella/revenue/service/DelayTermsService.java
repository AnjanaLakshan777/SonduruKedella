package com.sondurukedella.revenue.service;

import com.sondurukedella.activity.entity.ActivityKind;
import com.sondurukedella.activity.service.ActivityService;
import com.sondurukedella.common.exception.BusinessRuleException;
import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.revenue.dto.DelayTermsDraft;
import com.sondurukedella.revenue.dto.DelayTermsResponse;
import com.sondurukedella.revenue.dto.DelayTermsUpdateRequest;
import com.sondurukedella.revenue.entity.DelayDeductionTerms;
import com.sondurukedella.revenue.mapper.RevenueMapper;
import java.util.Optional;
import com.sondurukedella.revenue.repository.DelayDeductionTermsRepository;
import com.sondurukedella.revenue.validation.DelayTermsRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Creating the agreed terms only. Applying and reversing a deduction comes with the full module.
@Service
@RequiredArgsConstructor
public class DelayTermsService {

    private final DelayDeductionTermsRepository repository;
    private final RevenueMapper mapper;
    private final ActivityService activityService;

    // Empty for any project without terms - always the case for design and consultation work,
    // since the table only accepts construction projects. Nothing is filled in for them.
    @Transactional(readOnly = true)
    public Optional<DelayTermsResponse> findTerms(long projectId) {
        return repository.findByProjectId(projectId).map(mapper::toResponse);
    }

    // projectType is passed as the plain code (what the table stores) so Revenue doesn't have to
    // depend on the projects module, which already depends on us.
    @Transactional(propagation = Propagation.MANDATORY)
    public DelayDeductionTerms createTerms(long projectId, String projectType, DelayTermsDraft draft) {
        DelayTermsRules.validate(projectType, draft);
        if (repository.existsByProjectId(projectId)) {
            throw new BusinessRuleException("This project already has delay deduction terms.");
        }
        DelayDeductionTerms terms = new DelayDeductionTerms(projectId, projectType);
        fill(terms, draft);
        return repository.saveAndFlush(terms);
    }

    // Add, change or switch off the terms after the project exists. Not while a deduction is applied -
    // it was worked out from the old terms, so reverse it first. Locked like apply/reverse.
    @Transactional
    public DelayTermsResponse save(long projectId, String projectType, DelayTermsUpdateRequest request) {
        DelayTermsRules.validate(projectType, request.terms());
        Optional<DelayDeductionTerms> existing = repository.findByProjectIdForUpdate(projectId);
        DelayDeductionTerms terms;
        if (existing.isPresent()) {
            terms = existing.get();
            ConflictException.requireCurrentVersion(request.version(), terms.getVersion());
            if (terms.isApplied()) {
                throw new ConflictException("A delay deduction is applied. Reverse it before changing the terms.");
            }
        } else {
            terms = new DelayDeductionTerms(projectId, projectType);
        }
        fill(terms, request.terms());
        terms.setEnabled(request.enabled());
        terms = repository.saveAndFlush(terms);
        activityService.record(projectId, ActivityKind.DEDUCTION, request.enabled()
                ? (existing.isPresent() ? "Delay deduction terms updated." : "Delay deduction terms added.")
                : "Delay deduction terms removed.");
        return mapper.toResponse(terms);
    }

    // keep only the value the chosen rule uses, so a stale value can't be read as "the rate" later
    private static void fill(DelayDeductionTerms terms, DelayTermsDraft draft) {
        terms.setContractDeadline(draft.contractDeadline());
        terms.setRule(draft.rule());
        terms.setRatePerDay(null);
        terms.setPercentPerWeek(null);
        terms.setFixedAmount(null);
        switch (draft.rule()) {
            case PER_DAY -> terms.setRatePerDay(Money.amount(draft.ratePerDay()));
            case PERCENT_PER_WEEK -> terms.setPercentPerWeek(draft.percentPerWeek());
            case FIXED_AMOUNT -> terms.setFixedAmount(Money.amount(draft.fixedAmount()));
        }
        terms.setGraceDays(draft.graceDays() == null ? 0 : draft.graceDays());
        terms.setMaxDeduction(draft.maxDeduction() == null ? null : Money.amount(draft.maxDeduction()));
        terms.setNotes(draft.notes() == null || draft.notes().isBlank() ? null : draft.notes().strip());
    }
}
