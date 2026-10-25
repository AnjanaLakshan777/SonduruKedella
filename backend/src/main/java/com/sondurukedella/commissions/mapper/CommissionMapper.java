package com.sondurukedella.commissions.mapper;

import com.sondurukedella.commissions.dto.CommissionPaymentResponse;
import com.sondurukedella.commissions.dto.CommissionResponse;
import com.sondurukedella.commissions.entity.CommissionArrangement;
import com.sondurukedella.commissions.entity.CommissionPayment;
import com.sondurukedella.commissions.service.CommissionCalculator;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.projects.dto.ProjectResponse;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class CommissionMapper {

    // the amount is the stored one once confirmed; a draft shows what confirming would store
    public BigDecimal amountOf(CommissionArrangement arrangement, BigDecimal contractValue) {
        return arrangement.getCommissionAmount() != null
                ? Money.amount(arrangement.getCommissionAmount())
                : CommissionCalculator.amount(contractValue, arrangement.getRatePct());
    }

    public CommissionResponse toResponse(CommissionArrangement arrangement, ProjectResponse project, BigDecimal paid) {
        BigDecimal amount = amountOf(arrangement, project.contractValue());
        BigDecimal activePaid = Money.amount(paid);
        return new CommissionResponse(
                arrangement.getId(),
                arrangement.getProjectId(),
                project.code(),
                project.name(),
                project.type(),
                project.contractValue(),
                arrangement.getRecipientName(),
                arrangement.getRole(),
                CommissionCalculator.plain(arrangement.getRatePct()),
                amount,
                activePaid,
                CommissionCalculator.outstanding(arrangement.getStatus(), amount, activePaid),
                CommissionCalculator.state(arrangement.getStatus(), amount, activePaid),
                arrangement.getConfirmedOn(),
                arrangement.isPolicyException(),
                arrangement.getVoidedAt(),
                arrangement.getVoidReason(),
                arrangement.getCreatedAt(),
                arrangement.getUpdatedAt(),
                arrangement.getVersion());
    }

    public CommissionPaymentResponse toResponse(CommissionPayment payment, CommissionArrangement arrangement, String method) {
        return new CommissionPaymentResponse(
                payment.getId(),
                payment.getArrangementId(),
                arrangement.getProjectId(),
                arrangement.getRecipientName(),
                payment.getPaymentDate(),
                payment.getAmount(),
                payment.getPaymentMethodId(),
                method,
                payment.getReference(),
                payment.getNotes(),
                payment.getRequestId(),
                payment.isVoided(),
                payment.getVoidedAt(),
                payment.getVoidReason(),
                payment.getCreatedAt(),
                payment.getVersion());
    }
}
