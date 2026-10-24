package com.sondurukedella.revenue.mapper;

import com.sondurukedella.revenue.dto.DelayTermsResponse;
import com.sondurukedella.revenue.dto.InstallmentReceivableResponse;
import com.sondurukedella.revenue.dto.InstallmentResponse;
import com.sondurukedella.revenue.dto.ReceiptResponse;
import com.sondurukedella.revenue.dto.WriteOffResponse;
import com.sondurukedella.revenue.entity.ClientPayment;
import com.sondurukedella.revenue.entity.DelayDeductionTerms;
import com.sondurukedella.revenue.entity.Installment;
import com.sondurukedella.revenue.entity.ReceivableWriteOff;
import com.sondurukedella.revenue.service.ReceivableCalculator.Receivable;
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

    public InstallmentReceivableResponse toReceivable(Installment installment, Receivable receivable) {
        return new InstallmentReceivableResponse(
                installment.getId(),
                installment.getProjectId(),
                installment.getDescription(),
                installment.getDueDate(),
                installment.getAmount(),
                installment.getKind(),
                installment.isBilled(),
                installment.getBilledOn(),
                receivable.deduction(),
                receivable.net(),
                receivable.received(),
                receivable.writtenOff(),
                receivable.outstanding(),
                receivable.status(),
                receivable.daysOverdue(),
                installment.getVersion());
    }

    public ReceiptResponse toResponse(ClientPayment payment, long projectId, String methodName) {
        return new ReceiptResponse(
                payment.getId(),
                payment.getInstallmentId(),
                projectId,
                payment.getPaymentDate(),
                payment.getAmount(),
                payment.getPaymentMethodId(),
                methodName,
                payment.getReference(),
                payment.getNotes(),
                payment.getRequestId(),
                payment.isVoided(),
                payment.getVoidedAt(),
                payment.getVoidReason(),
                payment.getCreatedAt(),
                payment.getVersion());
    }

    public WriteOffResponse toResponse(ReceivableWriteOff writeOff, long projectId) {
        return new WriteOffResponse(
                writeOff.getId(),
                writeOff.getInstallmentId(),
                projectId,
                writeOff.getWriteOffDate(),
                writeOff.getAmount(),
                writeOff.getReason(),
                writeOff.getCreatedAt());
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
