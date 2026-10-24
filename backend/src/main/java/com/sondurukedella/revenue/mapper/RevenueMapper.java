package com.sondurukedella.revenue.mapper;

import com.sondurukedella.revenue.dto.DelayTermsResponse;
import com.sondurukedella.revenue.dto.InstallmentResponse;
import com.sondurukedella.revenue.entity.DelayDeductionTerms;
import com.sondurukedella.revenue.entity.Installment;
import org.springframework.stereotype.Component;

@Component
public class RevenueMapper {

    public InstallmentResponse toResponse(Installment installment) {
        return new InstallmentResponse(
                installment.getId(),
                installment.getProjectId(),
                installment.getDescription(),
                installment.getDueDate(),
                installment.getAmount(),
                installment.getKind(),
                installment.getBilledOn() != null,
                installment.getBilledOn(),
                installment.getVersion());
    }

    public DelayTermsResponse toResponse(DelayDeductionTerms terms) {
        return new DelayTermsResponse(
                terms.getId(),
                terms.getProjectId(),
                terms.isEnabled(),
                terms.getContractDeadline(),
                terms.getRule(),
                terms.getRatePerDay(),
                terms.getPercentPerWeek(),
                terms.getFixedAmount(),
                terms.getGraceDays(),
                terms.getMaxDeduction(),
                terms.getNotes(),
                terms.getActualCompletion(),
                terms.getAppliedAmount(),
                terms.getAppliedOn(),
                terms.getAppliedInstallmentId(),
                terms.getVersion());
    }
}
