package com.sondurukedella.revenue.service;

import com.sondurukedella.activity.entity.ActivityKind;
import com.sondurukedella.activity.service.ActivityService;
import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.revenue.dto.ReceiptCreateRequest;
import com.sondurukedella.revenue.dto.ReceiptResponse;
import com.sondurukedella.revenue.dto.ReceiptVoidRequest;
import com.sondurukedella.revenue.entity.ClientPayment;
import com.sondurukedella.revenue.entity.Installment;
import com.sondurukedella.revenue.mapper.RevenueMapper;
import com.sondurukedella.revenue.repository.ClientPaymentRepository;
import com.sondurukedella.revenue.service.ReceivableCalculator.Receivable;
import com.sondurukedella.settings.service.PaymentMethodService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Client receipts. A receipt settles a billed installment; it is never revenue itself.
@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final InstallmentLedger ledger;
    private final ClientPaymentRepository repository;
    private final RevenueMapper mapper;
    private final PaymentMethodService paymentMethods;
    private final ActivityService activityService;
    private final Clock clock;

    // The installment row is locked first, so two receipts for it run one after the other: the second
    // sees the first in the balance and can't push the total past what is owed. The same lock makes a
    // resent request (same requestId) find the receipt the first one saved.
    @Transactional
    public ReceiptResult record(long projectId, long installmentId, ReceiptCreateRequest request) {
        Installment installment = ledger.lockInProject(projectId, installmentId);

        Optional<ClientPayment> previous = repository.findByRequestId(request.requestId());
        if (previous.isPresent()) {
            return new ReceiptResult(replay(previous.get(), projectId, installmentId, request), false);
        }

        if (!installment.isBilled()) {
            throw new InvalidRequestException("Bill this installment before recording a payment against it.");
        }
        if (!paymentMethods.isActive(request.paymentMethodId())) {
            throw new InvalidRequestException("Choose a payment method from the Settings list.");
        }
        Receivable before = ledger.receivable(installment, LocalDate.now(clock));
        if (request.amount().compareTo(before.outstanding()) > 0) {
            throw new InvalidRequestException("More than the " + Money.format(before.outstanding())
                    + " still owed on " + installment.getDescription() + ".");
        }

        ClientPayment saved = repository.saveAndFlush(new ClientPayment(installment.getId(), request.paymentDate(),
                Money.amount(request.amount()), request.paymentMethodId(), trimToNull(request.reference()),
                trimToNull(request.notes()), request.requestId()));
        String method = methodName(saved.getPaymentMethodId());
        activityService.record(projectId, ActivityKind.PAYMENT, "Payment received: " + Money.format(saved.getAmount())
                + " for " + installment.getDescription() + " (" + method
                + (saved.getReference() == null ? "" : ", " + saved.getReference()) + ").");
        return new ReceiptResult(mapper.toResponse(saved, projectId, method), true);
    }

    // Voiding is the only correction: the row stays, with when and why, and stops counting.
    @Transactional
    public ReceiptResponse voidReceipt(long projectId, long receiptId, ReceiptVoidRequest request) {
        ClientPayment payment = findInProject(projectId, receiptId);
        Installment installment = ledger.lockInProject(projectId, payment.getInstallmentId());
        if (payment.isVoided()) {
            throw new ConflictException("This receipt was already voided.");
        }
        ConflictException.requireCurrentVersion(request.version(), payment.getVersion());

        payment.markVoided(OffsetDateTime.now(clock), request.reason().strip());
        payment = repository.saveAndFlush(payment);
        activityService.record(projectId, ActivityKind.PAYMENT, "Payment of " + Money.format(payment.getAmount())
                + " for " + installment.getDescription() + " voided: " + payment.getVoidReason());
        return mapper.toResponse(payment, projectId, methodName(payment.getPaymentMethodId()));
    }

    @Transactional(readOnly = true)
    public ReceiptResponse get(long projectId, long receiptId) {
        ClientPayment payment = findInProject(projectId, receiptId);
        return mapper.toResponse(payment, projectId, methodName(payment.getPaymentMethodId()));
    }

    private ClientPayment findInProject(long projectId, long receiptId) {
        ClientPayment payment = repository.findById(receiptId)
                .orElseThrow(() -> new NotFoundException("Receipt not found."));
        // goes through the installment so a receipt id from another project is a 404 too
        ledger.findInProject(projectId, payment.getInstallmentId());
        return payment;
    }

    // Same request again: hand back the first receipt. A reused id with different details is a
    // client bug, and silently returning the old receipt would hide it.
    private ReceiptResponse replay(ClientPayment previous, long projectId, long installmentId, ReceiptCreateRequest request) {
        boolean same = previous.getInstallmentId() == installmentId
                && previous.getAmount().compareTo(request.amount()) == 0
                && previous.getPaymentDate().equals(request.paymentDate())
                && previous.getPaymentMethodId().equals(request.paymentMethodId());
        if (!same) {
            throw new ConflictException("This request id was already used for a different payment.");
        }
        return mapper.toResponse(previous, projectId, methodName(previous.getPaymentMethodId()));
    }

    private String methodName(long paymentMethodId) {
        return paymentMethods.findNames(List.of(paymentMethodId)).get(paymentMethodId);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // created is false when an earlier receipt with the same requestId was returned instead
    public record ReceiptResult(ReceiptResponse receipt, boolean created) {
    }
}
