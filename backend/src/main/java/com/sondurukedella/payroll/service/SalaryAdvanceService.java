package com.sondurukedella.payroll.service;

import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.employees.dto.EmployeeResponse;
import com.sondurukedella.employees.entity.EmployeeStatus;
import com.sondurukedella.employees.service.EmployeeService;
import com.sondurukedella.payroll.dto.SalaryAdvanceRequest;
import com.sondurukedella.payroll.dto.SalaryAdvanceResponse;
import com.sondurukedella.payroll.dto.VoidRequest;
import com.sondurukedella.payroll.entity.SalaryAdvance;
import com.sondurukedella.payroll.mapper.PayrollMapper;
import com.sondurukedella.payroll.repository.AdvanceRecoveryRepository;
import com.sondurukedella.payroll.repository.AdvanceRecoveryRepository.AdvanceTotal;
import com.sondurukedella.payroll.repository.SalaryAdvanceRepository;
import com.sondurukedella.payroll.validation.PayrollRules;
import com.sondurukedella.settings.service.PaymentMethodService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Advances: cash a worker gets ahead of payday, recovered from net pay on the following paydays.
// Recording one is not a cost and not a wage - the week's gross is the labour cost either way.
@Service
@RequiredArgsConstructor
public class SalaryAdvanceService {

    public static final int MAX_PAGE_SIZE = 100;

    private final SalaryAdvanceRepository repository;
    private final AdvanceRecoveryRepository recoveries;
    private final PayrollMapper mapper;
    private final EmployeeService employees;
    private final PaymentMethodService paymentMethods;
    private final Clock clock;

    // the worker's lock (as for payments) so an advance and a freeze that recovers advances don't interleave
    @Transactional
    public AdvanceResult record(SalaryAdvanceRequest request) {
        EmployeeResponse employee = employees.lockForPayroll(request.employeeId())
                .orElseThrow(() -> new InvalidRequestException("The selected worker does not exist."));
        Optional<SalaryAdvance> previous = repository.findByRequestId(request.requestId());
        if (previous.isPresent()) {
            SalaryAdvance a = previous.get();
            boolean same = a.getEmployeeId() == employee.id() && a.getAmount().compareTo(request.amount()) == 0
                    && a.getPaymentDate().equals(request.paymentDate()) && a.getPaymentMethodId().equals(request.paymentMethodId());
            if (!same) {
                throw new ConflictException("This request id was already used for a different advance.");
            }
            return new AdvanceResult(toResponse(a, employee.name()), false);
        }
        if (employee.status() != EmployeeStatus.ACTIVE) {
            throw new InvalidRequestException(employee.name() + " is inactive and can't take a new advance.");
        }
        if (!paymentMethods.isActive(request.paymentMethodId())) {
            throw new InvalidRequestException("Choose a payment method from the Settings list.");
        }
        SalaryAdvance saved = repository.saveAndFlush(new SalaryAdvance(employee.id(), request.paymentDate(),
                Money.amount(request.amount()), request.paymentMethodId(), PayrollRules.trimToNull(request.reference()),
                PayrollRules.trimToNull(request.notes()), request.requestId()));
        return new AdvanceResult(toResponse(saved, employee.name()), true);
    }

    // Only an advance nothing has recovered yet: a recovery belongs to a paid, frozen week.
    @Transactional
    public SalaryAdvanceResponse voidAdvance(long id, VoidRequest request) {
        SalaryAdvance advance = repository.findById(id).orElseThrow(() -> new NotFoundException("Advance not found."));
        EmployeeResponse employee = employees.lockForPayroll(advance.getEmployeeId()).orElseThrow();
        if (advance.isVoided()) {
            throw new ConflictException("This advance was already voided.");
        }
        if (!recoveries.findByAdvanceId(id).isEmpty()) {
            throw new ConflictException("A paid week has already recovered part of this advance, so it can't be voided.");
        }
        ConflictException.requireCurrentVersion(request.version(), advance.getVersion());
        advance.markVoided(OffsetDateTime.now(clock), request.reason().strip());
        return toResponse(repository.saveAndFlush(advance), employee.name());
    }

    @Transactional(readOnly = true)
    public PageResponse<SalaryAdvanceResponse> search(Long employeeId, LocalDate from, LocalDate to, Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("The page size can be at most " + MAX_PAGE_SIZE + ".");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("from must be on or before to.");
        }
        Page<SalaryAdvance> page = repository.search(employeeId, from, to, PageRequest.of(pageable.getPageNumber(),
                pageable.getPageSize(), Sort.by(Sort.Order.desc("paymentDate"), Sort.Order.desc("id"))));
        List<SalaryAdvance> rows = page.getContent();
        Map<Long, BigDecimal> recovered = rows.isEmpty() ? Map.of() : recoveries.recoveredByAdvance(rows.stream().map(SalaryAdvance::getId).toList())
                .stream().collect(Collectors.toMap(AdvanceTotal::getAdvanceId, AdvanceTotal::getTotal));
        Map<Long, EmployeeResponse> people = employees.findAll(rows.stream().map(SalaryAdvance::getEmployeeId).collect(Collectors.toSet()));
        Map<Long, String> methods = paymentMethods.findNames(rows.stream().map(SalaryAdvance::getPaymentMethodId).collect(Collectors.toSet()));
        List<SalaryAdvanceResponse> content = rows.stream().map(a -> mapper.toResponse(a, people.get(a.getEmployeeId()).name(),
                methods.get(a.getPaymentMethodId()), Money.amount(recovered.getOrDefault(a.getId(), BigDecimal.ZERO)))).toList();
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private SalaryAdvanceResponse toResponse(SalaryAdvance a, String employeeName) {
        BigDecimal recovered = recoveries.recoveredByAdvance(List.of(a.getId())).stream().map(AdvanceTotal::getTotal).findFirst()
                .orElse(BigDecimal.ZERO);
        return mapper.toResponse(a, employeeName, paymentMethods.findNames(List.of(a.getPaymentMethodId())).get(a.getPaymentMethodId()),
                Money.amount(recovered));
    }

    // created is false when an earlier advance with the same requestId was returned instead
    public record AdvanceResult(SalaryAdvanceResponse advance, boolean created) {
    }
}
