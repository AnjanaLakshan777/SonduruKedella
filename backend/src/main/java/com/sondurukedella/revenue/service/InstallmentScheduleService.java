package com.sondurukedella.revenue.service;

import com.sondurukedella.activity.entity.ActivityKind;
import com.sondurukedella.activity.service.ActivityService;
import com.sondurukedella.common.exception.BusinessRuleException;
import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.revenue.dto.InstallmentDraft;
import com.sondurukedella.revenue.dto.InstallmentResponse;
import com.sondurukedella.revenue.dto.ScheduleRevisionRequest;
import com.sondurukedella.revenue.dto.VariationRequest;
import com.sondurukedella.revenue.entity.Installment;
import com.sondurukedella.revenue.entity.InstallmentKind;
import com.sondurukedella.revenue.mapper.RevenueMapper;
import com.sondurukedella.revenue.repository.InstallmentRepository;
import com.sondurukedella.revenue.validation.ScheduleRules;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// The payment plan: first schedule, revising unbilled rows, and variations. Billing, receipts and
// write-offs are in their own services.
@Service
@RequiredArgsConstructor
public class InstallmentScheduleService {

    private final InstallmentRepository repository;
    private final RevenueMapper mapper;
    private final ActivityService activityService;
    private final Clock clock;

    // Plain read, so unlike the write below it doesn't need the caller's transaction. It takes a
    // project id and trusts the caller (Projects) to have checked the project exists.
    @Transactional(readOnly = true)
    public List<InstallmentResponse> findSchedule(long projectId) {
        return repository.findByProjectIdOrderByDueDateAscIdAsc(projectId).stream().map(mapper::toResponse).toList();
    }

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

    // Billed rows can't change and the scheduled rows still have to match the contract value.
    // @Version on each row gives a 409 if someone bills one while we're revising.
    @Transactional
    public List<InstallmentResponse> revise(long projectId, BigDecimal contractValue, ScheduleRevisionRequest request) {
        ScheduleRules.validateInitialSchedule(contractValue, request.installments().stream().map(ScheduleRevisionRequest.Row::installment).toList());
        Map<Long, Installment> current = repository.findByProjectIdOrderByDueDateAscIdAsc(projectId).stream()
                .filter(i -> i.getKind() == InstallmentKind.SCHEDULED)
                .collect(Collectors.toMap(Installment::getId, Function.identity()));
        Set<Long> kept = new HashSet<>();
        for (ScheduleRevisionRequest.Row row : request.installments()) {
            InstallmentDraft draft = row.installment();
            String description = draft.description().strip();
            BigDecimal amount = Money.amount(draft.amount());
            if (row.id() == null) {
                repository.save(Installment.scheduled(projectId, description, draft.dueDate(), amount));
                continue;
            }
            Installment existing = current.get(row.id());
            if (existing == null || !kept.add(row.id())) {
                throw new NotFoundException("Installment " + row.id() + " is not a scheduled installment of this project.");
            }
            if (row.version() == null) {
                throw new InvalidRequestException("Send the version of installment " + row.id() + ".");
            }
            ConflictException.requireCurrentVersion(row.version(), existing.getVersion());
            boolean same = existing.getDescription().equals(description) && existing.getDueDate().equals(draft.dueDate())
                    && existing.getAmount().compareTo(amount) == 0;
            if (existing.isBilled() && !same) {
                throw new BusinessRuleException("\"" + existing.getDescription() + "\" is billed and can't be changed.");
            }
            if (!same) {
                existing.revise(description, draft.dueDate(), amount);
                repository.save(existing);
            }
        }
        for (Installment gone : current.values()) {
            if (!kept.contains(gone.getId())) {
                if (gone.isBilled()) {
                    throw new BusinessRuleException("\"" + gone.getDescription() + "\" is billed and can't be removed.");
                }
                repository.delete(gone);
            }
        }
        repository.flush();
        activityService.record(projectId, ActivityKind.NOTE, "Payment schedule revised.");
        return findSchedule(projectId);
    }

    @Transactional
    public InstallmentResponse addVariation(long projectId, VariationRequest request) {
        LocalDate billedOn = request.billedOn() != null ? request.billedOn() : LocalDate.now(clock);
        Installment saved = repository.saveAndFlush(Installment.variation(projectId, request.description().strip(), request.dueDate(),
                Money.amount(request.amount()), billedOn));
        activityService.record(projectId, ActivityKind.NOTE, "Variation billed: " + saved.getDescription() + " (" + Money.format(saved.getAmount()) + ").");
        return mapper.toResponse(saved);
    }
}
