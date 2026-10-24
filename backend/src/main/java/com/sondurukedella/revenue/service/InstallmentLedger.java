package com.sondurukedella.revenue.service;

import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.revenue.entity.DelayDeductionTerms;
import com.sondurukedella.revenue.entity.Installment;
import com.sondurukedella.revenue.repository.ClientPaymentRepository;
import com.sondurukedella.revenue.repository.DelayDeductionTermsRepository;
import com.sondurukedella.revenue.repository.InstallmentRepository;
import com.sondurukedella.revenue.repository.ReceivableWriteOffRepository;
import com.sondurukedella.revenue.service.ReceivableCalculator.Receivable;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Loads one installment and its current balance for the services that change money. Always called
// inside their transaction, after the installment row is locked, so the sums it reads can't move.
@Component
@RequiredArgsConstructor
class InstallmentLedger {

    private final InstallmentRepository installments;
    private final ClientPaymentRepository payments;
    private final ReceivableWriteOffRepository writeOffs;
    private final DelayDeductionTermsRepository terms;

    // An installment of another project is "not found", never quietly used: ids in the URL must agree.
    Installment lockInProject(long projectId, long installmentId) {
        return installments.findByIdForUpdate(installmentId)
                .filter(i -> i.getProjectId() == projectId)
                .orElseThrow(() -> new NotFoundException("Installment not found."));
    }

    Installment findInProject(long projectId, long installmentId) {
        return installments.findById(installmentId)
                .filter(i -> i.getProjectId() == projectId)
                .orElseThrow(() -> new NotFoundException("Installment not found."));
    }

    Receivable receivable(Installment installment, LocalDate today) {
        return ReceivableCalculator.of(installment.getAmount(), installment.isBilled(), installment.getDueDate(),
                deductionOn(installment.getId()), payments.sumActive(installment.getId()),
                writeOffs.sumFor(installment.getId()), today);
    }

    // What is still owed before any delay deduction - the ceiling for a new deduction.
    BigDecimal outstandingBeforeDeduction(Installment installment) {
        return installment.getAmount()
                .subtract(payments.sumActive(installment.getId()))
                .subtract(writeOffs.sumFor(installment.getId()))
                .max(BigDecimal.ZERO);
    }

    BigDecimal deductionOn(long installmentId) {
        return terms.findByAppliedInstallmentId(installmentId)
                .map(DelayDeductionTerms::getAppliedAmount)
                .orElse(BigDecimal.ZERO);
    }
}
