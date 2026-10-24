package com.sondurukedella.revenue.service;

import com.sondurukedella.common.exception.BusinessRuleException;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.revenue.dto.InstallmentDraft;
import com.sondurukedella.revenue.entity.Installment;
import com.sondurukedella.revenue.repository.InstallmentRepository;
import com.sondurukedella.revenue.validation.ScheduleRules;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Only the "first schedule at project creation" part of Revenue. Editing, billing, variations
// and payments are not built yet.
@Service
@RequiredArgsConstructor
public class InstallmentScheduleService {

    private final InstallmentRepository repository;

    // MANDATORY because a schedule without its project (or the other way round) must never be
    // committed - the caller's project transaction has to own this.
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Installment> createInitialSchedule(long projectId, BigDecimal contractValue, List<InstallmentDraft> rows) {
        ScheduleRules.validateInitialSchedule(contractValue, rows);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        if (repository.existsByProjectId(projectId)) {
            throw new BusinessRuleException("This project already has a payment schedule.");
        }
        List<Installment> installments = rows.stream()
                .map(row -> Installment.scheduled(projectId, row.description().strip(), row.dueDate(),
                        Money.amount(row.amount())))
                .toList();
        return repository.saveAllAndFlush(installments);
    }
}
