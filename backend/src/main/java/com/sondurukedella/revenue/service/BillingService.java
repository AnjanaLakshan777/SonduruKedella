package com.sondurukedella.revenue.service;

import com.sondurukedella.activity.entity.ActivityKind;
import com.sondurukedella.activity.service.ActivityService;
import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.revenue.dto.BillInstallmentRequest;
import com.sondurukedella.revenue.dto.InstallmentReceivableResponse;
import com.sondurukedella.revenue.entity.Installment;
import com.sondurukedella.revenue.mapper.RevenueMapper;
import com.sondurukedella.revenue.repository.InstallmentRepository;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Billing is its own explicit step: it is what turns a scheduled installment into revenue and a
// receivable. It never records money and never touches the contract value.
@Service
@RequiredArgsConstructor
public class BillingService {

    private final InstallmentLedger ledger;
    private final InstallmentRepository repository;
    private final RevenueMapper mapper;
    private final ActivityService activityService;
    private final Clock clock;

    @Transactional
    public InstallmentReceivableResponse bill(long projectId, long installmentId, BillInstallmentRequest request) {
        Installment installment = ledger.lockInProject(projectId, installmentId);
        // checked before the version on purpose: a double click should hear "already billed", not "reload"
        if (installment.isBilled()) {
            throw new ConflictException("This installment was already billed on " + installment.getBilledOn() + ".");
        }
        ConflictException.requireCurrentVersion(request.version(), installment.getVersion());

        LocalDate today = LocalDate.now(clock);
        installment.bill(request.billedOn() == null ? today : request.billedOn());
        installment = repository.saveAndFlush(installment);
        activityService.record(projectId, ActivityKind.NOTE, "Installment billed: " + installment.getDescription()
                + " (" + Money.format(installment.getAmount()) + ").");
        return mapper.toReceivable(installment, ledger.receivable(installment, today));
    }
}
