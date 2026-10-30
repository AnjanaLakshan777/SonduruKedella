package com.sondurukedella.revenue.service;

import com.sondurukedella.common.money.Money;
import com.sondurukedella.revenue.dto.InstallmentReceivableDetail;
import com.sondurukedella.revenue.dto.InstallmentReceivableResponse;
import com.sondurukedella.revenue.dto.ProjectReceivablesResponse;
import com.sondurukedella.revenue.dto.ReceiptResponse;
import com.sondurukedella.revenue.dto.ReceivableStatus;
import com.sondurukedella.revenue.dto.ReceivableTotals;
import com.sondurukedella.revenue.dto.ReceivablesSummaryResponse;
import com.sondurukedella.revenue.dto.WriteOffResponse;
import com.sondurukedella.revenue.entity.ClientPayment;
import com.sondurukedella.revenue.entity.DelayDeductionTerms;
import com.sondurukedella.revenue.entity.Installment;
import com.sondurukedella.revenue.mapper.RevenueMapper;
import com.sondurukedella.revenue.repository.ClientPaymentRepository;
import com.sondurukedella.revenue.repository.DelayDeductionTermsRepository;
import com.sondurukedella.revenue.repository.InstallmentRepository;
import com.sondurukedella.revenue.repository.InstallmentTotal;
import com.sondurukedella.revenue.repository.ReceivableWriteOffRepository;
import com.sondurukedella.revenue.service.ReceivableCalculator.Receivable;
import com.sondurukedella.settings.service.PaymentMethodService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Read-only views of what is owed. Nothing here writes; every figure is worked out from the stored
// installments, receipts, write-offs and applied deduction, with "today" in Colombo.
@Service
@RequiredArgsConstructor
public class ReceivableService {

    private final InstallmentLedger ledger;
    private final InstallmentRepository installments;
    private final ClientPaymentRepository payments;
    private final ReceivableWriteOffRepository writeOffs;
    private final DelayDeductionTermsRepository terms;
    private final RevenueMapper mapper;
    private final PaymentMethodService paymentMethods;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ProjectReceivablesResponse forProject(long projectId) {
        List<Installment> schedule = installments.findByProjectIdOrderByDueDateAscIdAsc(projectId);
        List<InstallmentReceivableResponse> rows = receivables(schedule);
        return new ProjectReceivablesResponse(projectId, rows, totals(rows));
    }

    @Transactional(readOnly = true)
    public InstallmentReceivableDetail installment(long projectId, long installmentId) {
        Installment installment = ledger.findInProject(projectId, installmentId);
        InstallmentReceivableResponse row = mapper.toReceivable(installment,
                ledger.receivable(installment, LocalDate.now(clock)));
        List<WriteOffResponse> writeOffRows = writeOffs.findByInstallmentIdInOrderByWriteOffDateAscIdAsc(List.of(installmentId))
                .stream().map(w -> mapper.toResponse(w, projectId)).toList();
        return new InstallmentReceivableDetail(row, receipts(projectId, List.of(installmentId)), writeOffRows);
    }

    // every receipt of the project, voided ones included (flagged)
    @Transactional(readOnly = true)
    public List<ReceiptResponse> receiptsForProject(long projectId) {
        List<Long> ids = installments.findByProjectIdOrderByDueDateAscIdAsc(projectId).stream().map(Installment::getId).toList();
        return receipts(projectId, ids);
    }

    // every write-off of the project, reversed ones included (flagged)
    @Transactional(readOnly = true)
    public List<WriteOffResponse> writeOffsForProject(long projectId) {
        List<Long> ids = installments.findByProjectIdOrderByDueDateAscIdAsc(projectId).stream().map(Installment::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return writeOffs.findByInstallmentIdInOrderByWriteOffDateAscIdAsc(ids).stream().map(w -> mapper.toResponse(w, projectId)).toList();
    }

    // all billed installments with their numbers - the receivables report uses this so the math matches
    @Transactional(readOnly = true)
    public List<InstallmentReceivableResponse> billedReceivables() {
        return receivables(installments.findByBilledOnIsNotNull());
    }

    // Company-wide KPIs for the Receivables tab. Only billed installments are receivables.
    @Transactional(readOnly = true)
    public ReceivablesSummaryResponse summary() {
        List<InstallmentReceivableResponse> rows = receivables(installments.findByBilledOnIsNotNull());
        BigDecimal outstanding = sum(rows, r -> r.outstanding());
        BigDecimal overdue = sum(rows.stream().filter(r -> r.status() == ReceivableStatus.OVERDUE).toList(), r -> r.outstanding());
        List<InstallmentReceivableResponse> partly = rows.stream()
                .filter(r -> r.outstanding().signum() > 0 && r.received().add(r.writtenOff()).signum() > 0).toList();
        return new ReceivablesSummaryResponse(outstanding, overdue, sum(partly, r -> r.outstanding()),
                sum(rows, r -> r.writtenOff()),
                rows.stream().filter(r -> r.outstanding().signum() > 0).count(),
                rows.stream().filter(r -> r.status() == ReceivableStatus.OVERDUE).count(),
                partly.size());
    }

    private List<InstallmentReceivableResponse> receivables(List<Installment> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(Installment::getId).toList();
        Map<Long, BigDecimal> received = byInstallment(payments.sumActiveByInstallment(ids));
        Map<Long, BigDecimal> written = byInstallment(writeOffs.sumByInstallment(ids));
        Map<Long, BigDecimal> deductions = terms.findByAppliedInstallmentIdIn(ids).stream()
                .collect(Collectors.toMap(DelayDeductionTerms::getAppliedInstallmentId, DelayDeductionTerms::getAppliedAmount));
        LocalDate today = LocalDate.now(clock);
        return list.stream().map(i -> {
            Receivable r = ReceivableCalculator.of(i.getAmount(), i.isBilled(), i.getDueDate(),
                    deductions.getOrDefault(i.getId(), BigDecimal.ZERO), received.getOrDefault(i.getId(), BigDecimal.ZERO),
                    written.getOrDefault(i.getId(), BigDecimal.ZERO), today);
            return mapper.toReceivable(i, r);
        }).toList();
    }

    private static ReceivableTotals totals(List<InstallmentReceivableResponse> rows) {
        List<InstallmentReceivableResponse> billed = rows.stream().filter(InstallmentReceivableResponse::billed).toList();
        List<InstallmentReceivableResponse> unbilled = rows.stream().filter(r -> !r.billed()).toList();
        return new ReceivableTotals(
                sum(billed, r -> r.amount()),
                sum(billed, r -> r.deduction()),
                sum(billed, r -> r.net()),
                sum(billed, r -> r.received()),
                sum(billed, r -> r.writtenOff()),
                sum(billed, r -> r.outstanding()),
                sum(billed.stream().filter(r -> r.status() == ReceivableStatus.OVERDUE).toList(), r -> r.outstanding()),
                sum(unbilled, r -> r.amount()));
    }

    private List<ReceiptResponse> receipts(long projectId, Collection<Long> installmentIds) {
        if (installmentIds.isEmpty()) {
            return List.of();
        }
        List<ClientPayment> list = payments.findByInstallmentIdInOrderByPaymentDateAscIdAsc(installmentIds);
        Map<Long, String> names = paymentMethods.findNames(
                list.stream().map(ClientPayment::getPaymentMethodId).collect(Collectors.toSet()));
        return list.stream().map(p -> mapper.toResponse(p, projectId, names.get(p.getPaymentMethodId()))).toList();
    }

    private static Map<Long, BigDecimal> byInstallment(List<InstallmentTotal> rows) {
        return rows.stream().collect(Collectors.toMap(InstallmentTotal::getInstallmentId, InstallmentTotal::getTotal));
    }

    private static BigDecimal sum(List<InstallmentReceivableResponse> rows,
            Function<InstallmentReceivableResponse, BigDecimal> field) {
        return Money.amount(rows.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
