package com.sondurukedella.expenses.service;

import com.sondurukedella.activity.entity.ActivityKind;
import com.sondurukedella.activity.service.ActivityService;
import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.expenses.dto.ExpensePaymentRequest;
import com.sondurukedella.expenses.dto.ExpensePaymentResponse;
import com.sondurukedella.expenses.dto.ExpensePaymentVoidRequest;
import com.sondurukedella.expenses.entity.Expense;
import com.sondurukedella.expenses.entity.ExpensePayment;
import com.sondurukedella.expenses.mapper.ExpenseMapper;
import com.sondurukedella.expenses.repository.ExpensePaymentRepository;
import com.sondurukedella.expenses.repository.ExpenseRepository;
import com.sondurukedella.expenses.validation.ExpenseRules;
import com.sondurukedella.settings.service.PaymentMethodService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Payments settle an expense; they are never a second expense. Paid and outstanding always come
// from the stored payments, never from anything the client sends.
@Service
@RequiredArgsConstructor
public class ExpensePaymentService {

    private final ExpenseRepository expenses;
    private final ExpensePaymentRepository repository;
    private final ExpenseMapper mapper;
    private final PaymentMethodService paymentMethods;
    private final ActivityService activityService;
    private final Clock clock;

    // The expense row is locked first, so concurrent payments queue up: the second one sees the first
    // in the balance and can't overpay. The same lock lets a resent request find the payment the first
    // one saved and return it.
    @Transactional
    public PaymentResult record(long expenseId, ExpensePaymentRequest request) {
        Expense expense = expenses.findByIdForUpdate(expenseId).orElseThrow(ExpensePaymentService::expenseNotFound);
        return recordOn(expense, request);
    }

    // Also used by ExpenseService for the "paid in full / part" choice, inside its create transaction.
    PaymentResult recordOn(Expense expense, ExpensePaymentRequest request) {
        Optional<ExpensePayment> previous = repository.findByRequestId(request.requestId());
        if (previous.isPresent()) {
            return new PaymentResult(replay(previous.get(), expense.getId(), request), false);
        }
        if (!paymentMethods.isActive(request.paymentMethodId())) {
            throw new InvalidRequestException("Choose a payment method from the Settings list.");
        }
        BigDecimal outstanding = expense.getAmount().subtract(repository.sumActive(expense.getId())).max(BigDecimal.ZERO);
        if (request.amount().compareTo(outstanding) > 0) {
            throw new InvalidRequestException("More than the " + Money.format(outstanding) + " still unpaid on this expense.");
        }

        ExpensePayment saved = repository.saveAndFlush(new ExpensePayment(expense.getId(), request.paymentDate(),
                Money.amount(request.amount()), request.paymentMethodId(), ExpenseRules.trimToNull(request.reference()),
                ExpenseRules.trimToNull(request.notes()), request.requestId()));
        String method = methodName(saved.getPaymentMethodId());
        activityService.record(expense.getProjectId(), ActivityKind.EXPENSE, "Expense payment: " + Money.format(saved.getAmount())
                + " to " + expense.getPayee() + " (" + method + (saved.getReference() == null ? "" : ", " + saved.getReference()) + ").");
        return new PaymentResult(mapper.toResponse(saved, method), true);
    }

    // The only correction a payment allows. The row stays, with when and why, and stops counting.
    @Transactional
    public ExpensePaymentResponse voidPayment(long expenseId, long paymentId, ExpensePaymentVoidRequest request) {
        Expense expense = expenses.findByIdForUpdate(expenseId).orElseThrow(ExpensePaymentService::expenseNotFound);
        ExpensePayment payment = repository.findById(paymentId)
                .filter(p -> p.getExpenseId() == expenseId) // a payment of another expense is not found here
                .orElseThrow(() -> new NotFoundException("Payment not found."));
        if (payment.isVoided()) {
            throw new ConflictException("This payment was already voided.");
        }
        ConflictException.requireCurrentVersion(request.version(), payment.getVersion());

        payment.markVoided(OffsetDateTime.now(clock), request.reason().strip());
        payment = repository.saveAndFlush(payment);
        activityService.record(expense.getProjectId(), ActivityKind.EXPENSE, "Expense payment of "
                + Money.format(payment.getAmount()) + " to " + expense.getPayee() + " voided: " + payment.getVoidReason());
        return mapper.toResponse(payment, methodName(payment.getPaymentMethodId()));
    }

    @Transactional(readOnly = true)
    public List<ExpensePaymentResponse> list(long expenseId) {
        if (!expenses.existsById(expenseId)) {
            throw expenseNotFound();
        }
        List<ExpensePayment> payments = repository.findByExpenseIdOrderByPaymentDateAscIdAsc(expenseId);
        Map<Long, String> names = paymentMethods.findNames(
                payments.stream().map(ExpensePayment::getPaymentMethodId).collect(Collectors.toSet()));
        return payments.stream().map(p -> mapper.toResponse(p, names.get(p.getPaymentMethodId()))).toList();
    }

    // every payment of every expense, voided ones included (flagged), so the frontend doesn't need one call per expense
    @Transactional(readOnly = true)
    public List<ExpensePaymentResponse> listAll() {
        List<ExpensePayment> payments = repository.findAll(Sort.by("paymentDate", "id"));
        Map<Long, String> names = paymentMethods.findNames(
                payments.stream().map(ExpensePayment::getPaymentMethodId).collect(Collectors.toSet()));
        return payments.stream().map(p -> mapper.toResponse(p, names.get(p.getPaymentMethodId()))).toList();
    }

    // A resent submission gets the first payment back. The same id with different details is a client
    // bug, and quietly returning the old payment would hide it.
    private ExpensePaymentResponse replay(ExpensePayment previous, long expenseId, ExpensePaymentRequest request) {
        boolean same = previous.getExpenseId() == expenseId
                && previous.getAmount().compareTo(request.amount()) == 0
                && previous.getPaymentDate().equals(request.paymentDate())
                && previous.getPaymentMethodId().equals(request.paymentMethodId());
        if (!same) {
            throw new ConflictException("This request id was already used for a different payment.");
        }
        return mapper.toResponse(previous, methodName(previous.getPaymentMethodId()));
    }

    private String methodName(long id) {
        return paymentMethods.findNames(List.of(id)).get(id);
    }

    static NotFoundException expenseNotFound() {
        return new NotFoundException("Expense not found.");
    }

    // created is false when an earlier payment with the same requestId was returned instead
    public record PaymentResult(ExpensePaymentResponse payment, boolean created) {
    }
}
