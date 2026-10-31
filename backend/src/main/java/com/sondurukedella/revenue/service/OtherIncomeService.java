package com.sondurukedella.revenue.service;

import com.sondurukedella.common.exception.BusinessRuleException;
import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.revenue.dto.OtherIncomeCreateRequest;
import com.sondurukedella.revenue.dto.OtherIncomeReceiptResponse;
import com.sondurukedella.revenue.dto.OtherIncomeResponse;
import com.sondurukedella.revenue.dto.OtherIncomeResponse.Status;
import com.sondurukedella.revenue.dto.OtherIncomeSummary;
import com.sondurukedella.revenue.dto.OtherIncomeUpdateRequest;
import com.sondurukedella.revenue.dto.ReceiptCreateRequest;
import com.sondurukedella.revenue.dto.ReceiptVoidRequest;
import com.sondurukedella.revenue.entity.OtherIncome;
import com.sondurukedella.revenue.entity.OtherIncomeReceipt;
import com.sondurukedella.revenue.repository.OtherIncomeReceiptRepository;
import com.sondurukedella.revenue.repository.OtherIncomeRepository;
import com.sondurukedella.settings.service.PaymentMethodService;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Other income (not from a project). Counts on its income date, receipts only settle it.
// Receipts lock the income row, use requestId so a resend is harmless, and get voided instead of deleted.
@Service
@RequiredArgsConstructor
public class OtherIncomeService {

    public static final int MAX_PAGE_SIZE = 100;

    private final OtherIncomeRepository incomes;
    private final OtherIncomeReceiptRepository receipts;
    private final PaymentMethodService paymentMethods;
    private final Clock clock;

    @Transactional
    public OtherIncomeResponse create(OtherIncomeCreateRequest request) {
        if (request.dueDate() != null && request.dueDate().isBefore(request.date())) {
            throw new InvalidRequestException("The due date can't be before the income date.");
        }
        OtherIncome saved = incomes.saveAndFlush(new OtherIncome(request.date(), request.description().strip(), trimToNull(request.payer()),
                Money.amount(request.amount()), request.dueDate(), trimToNull(request.reference()), trimToNull(request.notes())));
        return toResponse(saved, BigDecimal.ZERO, today());
    }

    // locked so an edit and a receipt can't cross
    @Transactional
    public OtherIncomeResponse update(long id, OtherIncomeUpdateRequest request) {
        OtherIncome income = incomes.findByIdForUpdate(id).orElseThrow(OtherIncomeService::notFound);
        ConflictException.requireCurrentVersion(request.version(), income.getVersion());
        if (request.dueDate() != null && request.dueDate().isBefore(request.date())) {
            throw new InvalidRequestException("The due date can't be before the income date.");
        }
        BigDecimal received = receipts.sumActive(id);
        if (request.amount().compareTo(received) < 0) {
            throw new BusinessRuleException(Money.format(received) + " has already been received, so the amount can't be less.");
        }
        income.revise(request.date(), request.description().strip(), trimToNull(request.payer()), Money.amount(request.amount()),
                request.dueDate(), trimToNull(request.reference()), trimToNull(request.notes()));
        return toResponse(incomes.saveAndFlush(income), received, today());
    }

    // even a voided receipt blocks the delete - we keep its history
    @Transactional
    public void delete(long id) {
        OtherIncome income = incomes.findByIdForUpdate(id).orElseThrow(OtherIncomeService::notFound);
        if (receipts.existsByOtherIncomeId(id)) {
            throw new BusinessRuleException("This income has receipts and can't be deleted. Edit it instead.");
        }
        incomes.delete(income);
    }

    @Transactional(readOnly = true)
    public OtherIncomeResponse get(long id) {
        OtherIncome income = incomes.findById(id).orElseThrow(OtherIncomeService::notFound);
        return toResponse(income, receipts.sumActive(id), today());
    }

    // status depends on receipts so it's filtered after loading
    @Transactional(readOnly = true)
    public PageResponse<OtherIncomeResponse> search(Status status, LocalDate from, LocalDate to, Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("The page size can be at most " + MAX_PAGE_SIZE + ".");
        }
        if (pageable.getSort().isSorted()) {
            throw new InvalidRequestException("Other income is always listed newest first.");
        }
        List<OtherIncomeResponse> rows = rows(status, from, to);
        int size = pageable.getPageSize();
        int start = (int) Math.min((long) pageable.getPageNumber() * size, rows.size());
        int end = Math.min(start + size, rows.size());
        return new PageResponse<>(rows.subList(start, end), pageable.getPageNumber(), size, rows.size(), (rows.size() + size - 1) / size);
    }

    @Transactional(readOnly = true)
    public OtherIncomeSummary summary(Status status, LocalDate from, LocalDate to) {
        List<OtherIncomeResponse> rows = rows(status, from, to);
        return new OtherIncomeSummary(rows.size(), sum(rows, OtherIncomeResponse::amount), sum(rows, OtherIncomeResponse::received),
                sum(rows, OtherIncomeResponse::outstanding),
                sum(rows.stream().filter(r -> r.status() == Status.OVERDUE).toList(), OtherIncomeResponse::outstanding));
    }

    @Transactional
    public ReceiptResult receive(long id, ReceiptCreateRequest request) {
        OtherIncome income = incomes.findByIdForUpdate(id).orElseThrow(OtherIncomeService::notFound);
        Optional<OtherIncomeReceipt> previous = receipts.findByRequestId(request.requestId());
        if (previous.isPresent()) {
            OtherIncomeReceipt p = previous.get();
            boolean same = p.getOtherIncomeId().equals(id) && p.getAmount().compareTo(request.amount()) == 0
                    && p.getPaymentDate().equals(request.paymentDate()) && p.getPaymentMethodId().equals(request.paymentMethodId());
            if (!same) {
                throw new ConflictException("This request id was already used for a different receipt.");
            }
            return new ReceiptResult(toResponse(p, methodName(p.getPaymentMethodId())), false);
        }
        if (!paymentMethods.isActive(request.paymentMethodId())) {
            throw new InvalidRequestException("Choose a payment method from the Settings list.");
        }
        BigDecimal outstanding = income.getAmount().subtract(receipts.sumActive(id)).max(BigDecimal.ZERO);
        if (request.amount().compareTo(outstanding) > 0) {
            throw new InvalidRequestException("More than the " + Money.format(outstanding) + " still to receive.");
        }
        OtherIncomeReceipt saved = receipts.saveAndFlush(new OtherIncomeReceipt(id, request.paymentDate(), Money.amount(request.amount()),
                request.paymentMethodId(), trimToNull(request.reference()), trimToNull(request.notes()), request.requestId()));
        return new ReceiptResult(toResponse(saved, methodName(saved.getPaymentMethodId())), true);
    }

    @Transactional
    public OtherIncomeReceiptResponse voidReceipt(long id, long receiptId, ReceiptVoidRequest request) {
        incomes.findByIdForUpdate(id).orElseThrow(OtherIncomeService::notFound);
        OtherIncomeReceipt receipt = receipts.findById(receiptId).filter(r -> r.getOtherIncomeId() == id)
                .orElseThrow(() -> new NotFoundException("Receipt not found."));
        if (receipt.isVoided()) {
            throw new ConflictException("This receipt was already voided.");
        }
        ConflictException.requireCurrentVersion(request.version(), receipt.getVersion());
        receipt.markVoided(OffsetDateTime.now(clock), request.reason().strip());
        receipt = receipts.saveAndFlush(receipt);
        return toResponse(receipt, methodName(receipt.getPaymentMethodId()));
    }

    // includes voided, oldest first
    @Transactional(readOnly = true)
    public List<OtherIncomeReceiptResponse> listReceipts(long id) {
        if (!incomes.existsById(id)) {
            throw notFound();
        }
        List<OtherIncomeReceipt> list = receipts.findByOtherIncomeIdOrderByPaymentDateAscIdAsc(id);
        Map<Long, String> names = paymentMethods.findNames(list.stream().map(OtherIncomeReceipt::getPaymentMethodId).collect(Collectors.toSet()));
        return list.stream().map(r -> toResponse(r, names.get(r.getPaymentMethodId()))).toList();
    }

    private List<OtherIncomeResponse> rows(Status status, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("The start date must be on or before the end date.");
        }
        Specification<OtherIncome> range = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("incomeDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("incomeDate"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        List<OtherIncome> list = incomes.findAll(range, Sort.by(Sort.Order.desc("incomeDate"), Sort.Order.desc("id")));
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> received = receipts.sumActiveByIncome(list.stream().map(OtherIncome::getId).toList()).stream()
                .collect(Collectors.toMap(OtherIncomeReceiptRepository.IncomeTotal::getId, OtherIncomeReceiptRepository.IncomeTotal::getTotal));
        LocalDate today = today();
        return list.stream().map(i -> toResponse(i, received.getOrDefault(i.getId(), BigDecimal.ZERO), today))
                .filter(r -> status == null || r.status() == status).toList();
    }

    private static OtherIncomeResponse toResponse(OtherIncome i, BigDecimal receivedAmount, LocalDate today) {
        BigDecimal received = Money.amount(receivedAmount);
        BigDecimal outstanding = Money.amount(i.getAmount().subtract(received).max(BigDecimal.ZERO));
        Status status = outstanding.signum() == 0 ? Status.PAID
                // same rule as installments: owed from the day it was recorded, so unpaid means overdue
                : !i.getIncomeDate().isAfter(today) ? Status.OVERDUE
                : received.signum() > 0 ? Status.PARTIALLY_PAID : Status.DUE;
        return new OtherIncomeResponse(i.getId(), i.getIncomeDate(), i.getDescription(), i.getPayer(), Money.amount(i.getAmount()),
                i.getDueDate(), i.getReference(), i.getNotes(), received, outstanding, status, i.getCreatedAt(), i.getVersion());
    }

    private static OtherIncomeReceiptResponse toResponse(OtherIncomeReceipt r, String method) {
        return new OtherIncomeReceiptResponse(r.getId(), r.getOtherIncomeId(), r.getPaymentDate(), r.getAmount(), r.getPaymentMethodId(),
                method, r.getReference(), r.getNotes(), r.getRequestId(), r.isVoided(), r.getVoidedAt(), r.getVoidReason(), r.getCreatedAt(),
                r.getVersion());
    }

    private String methodName(long id) {
        return paymentMethods.findNames(List.of(id)).get(id);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> field) {
        return Money.amount(rows.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    static NotFoundException notFound() {
        return new NotFoundException("Other income not found.");
    }

    // created=false when the requestId was already used
    public record ReceiptResult(OtherIncomeReceiptResponse receipt, boolean created) {
    }
}
