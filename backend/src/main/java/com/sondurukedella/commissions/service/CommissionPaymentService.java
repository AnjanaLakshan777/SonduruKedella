package com.sondurukedella.commissions.service;

import com.sondurukedella.activity.entity.ActivityKind;
import com.sondurukedella.activity.service.ActivityService;
import com.sondurukedella.commissions.dto.CommissionPaymentFilter;
import com.sondurukedella.commissions.dto.CommissionPaymentRequest;
import com.sondurukedella.commissions.dto.CommissionPaymentResponse;
import com.sondurukedella.commissions.dto.CommissionPaymentVoidRequest;
import com.sondurukedella.commissions.entity.CommissionArrangement;
import com.sondurukedella.commissions.entity.CommissionPayment;
import com.sondurukedella.commissions.mapper.CommissionMapper;
import com.sondurukedella.commissions.repository.CommissionArrangementRepository;
import com.sondurukedella.commissions.repository.CommissionPaymentRepository;
import com.sondurukedella.commissions.validation.CommissionRules;
import com.sondurukedella.common.exception.BusinessRuleException;
import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.settings.service.PaymentMethodService;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// payments only settle a confirmed commission, they're never an extra cost
@Service
@RequiredArgsConstructor
public class CommissionPaymentService {

    public static final int MAX_PAGE_SIZE = 100;

    private final CommissionArrangementRepository arrangements;
    private final CommissionPaymentRepository repository;
    private final CommissionMapper mapper;
    private final PaymentMethodService paymentMethods;
    private final ActivityService activityService;
    private final Clock clock;

    // lock the arrangement first: concurrent payments queue up (no overpaying) and a resend
    // finds the payment the first request saved
    @Transactional
    public PaymentResult record(long commissionId, CommissionPaymentRequest request) {
        CommissionArrangement arrangement = arrangements.findByIdForUpdate(commissionId).orElseThrow(CommissionService::notFound);
        Optional<CommissionPayment> previous = repository.findByRequestId(request.requestId());
        if (previous.isPresent()) {
            return new PaymentResult(replay(previous.get(), arrangement, request), false);
        }
        if (arrangement.isPending()) {
            throw new BusinessRuleException("Confirm the commission before paying it.");
        }
        if (!arrangement.isConfirmed()) {
            throw new BusinessRuleException("This commission was voided and can't be paid.");
        }
        if (request.paymentDate().isAfter(LocalDate.now(clock))) {
            throw new InvalidRequestException("The payment date can't be in the future.");
        }
        if (!paymentMethods.isActive(request.paymentMethodId())) {
            throw new InvalidRequestException("Choose a payment method from the Settings list.");
        }
        BigDecimal outstanding = arrangement.getCommissionAmount().subtract(repository.sumActive(commissionId)).max(BigDecimal.ZERO);
        if (request.amount().compareTo(outstanding) > 0) {
            throw new InvalidRequestException("More than the " + Money.format(outstanding) + " still owed on this commission.");
        }

        CommissionPayment saved = repository.saveAndFlush(new CommissionPayment(commissionId, request.paymentDate(),
                Money.amount(request.amount()), request.paymentMethodId(), CommissionRules.trimToNull(request.reference()),
                CommissionRules.trimToNull(request.notes()), request.requestId()));
        String method = methodName(saved.getPaymentMethodId());
        activityService.record(arrangement.getProjectId(), ActivityKind.COMMISSION, "Commission payment: " + Money.format(saved.getAmount())
                + " to " + arrangement.getRecipientName() + " (" + method + (saved.getReference() == null ? "" : ", " + saved.getReference()) + ").");
        return new PaymentResult(mapper.toResponse(saved, arrangement, method), true);
    }

    // the row stays, it just stops counting
    @Transactional
    public CommissionPaymentResponse voidPayment(long commissionId, long paymentId, CommissionPaymentVoidRequest request) {
        CommissionArrangement arrangement = arrangements.findByIdForUpdate(commissionId).orElseThrow(CommissionService::notFound);
        CommissionPayment payment = repository.findById(paymentId)
                .filter(p -> p.getArrangementId() == commissionId) // a payment of another commission is not found here
                .orElseThrow(() -> new NotFoundException("Payment not found."));
        if (payment.isVoided()) {
            throw new ConflictException("This payment was already voided.");
        }
        ConflictException.requireCurrentVersion(request.version(), payment.getVersion());

        payment.markVoided(OffsetDateTime.now(clock), request.reason().strip());
        payment = repository.saveAndFlush(payment);
        activityService.record(arrangement.getProjectId(), ActivityKind.COMMISSION, "Commission payment of "
                + Money.format(payment.getAmount()) + " to " + arrangement.getRecipientName() + " voided: " + payment.getVoidReason());
        return mapper.toResponse(payment, arrangement, methodName(payment.getPaymentMethodId()));
    }

    @Transactional(readOnly = true)
    public List<CommissionPaymentResponse> list(long commissionId) {
        return forArrangement(arrangements.findById(commissionId).orElseThrow(CommissionService::notFound));
    }

    // includes voided ones so the history is complete
    @Transactional(readOnly = true)
    public List<CommissionPaymentResponse> forArrangement(CommissionArrangement arrangement) {
        List<CommissionPayment> payments = repository.findByArrangementIdOrderByPaymentDateAscIdAsc(arrangement.getId());
        Map<Long, String> names = methodNames(payments);
        return payments.stream().map(p -> mapper.toResponse(p, arrangement, names.get(p.getPaymentMethodId()))).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<CommissionPaymentResponse> search(CommissionPaymentFilter filter, Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("The page size can be at most " + MAX_PAGE_SIZE + ".");
        }
        if (pageable.getSort().isSorted()) {
            throw new InvalidRequestException("Commission payments are always listed newest first.");
        }
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new InvalidRequestException("The start date must be on or before the end date.");
        }
        List<Long> inProject = filter.projectId() == null ? null
                : arrangements.findByProjectIdOrderByIdAsc(filter.projectId()).stream().map(CommissionArrangement::getId).toList();
        Page<CommissionPayment> page = repository.findAll(matching(filter, inProject), PageRequest.of(pageable.getPageNumber(),
                pageable.getPageSize(), Sort.by(Sort.Order.desc("paymentDate"), Sort.Order.desc("id"))));
        Map<Long, CommissionArrangement> owners = arrangements.findAllById(page.getContent().stream()
                .map(CommissionPayment::getArrangementId).distinct().toList()).stream()
                .collect(Collectors.toMap(CommissionArrangement::getId, Function.identity()));
        Map<Long, String> names = methodNames(page.getContent());
        return PageResponse.of(page, p -> mapper.toResponse(p, owners.get(p.getArrangementId()), names.get(p.getPaymentMethodId())));
    }

    private static Specification<CommissionPayment> matching(CommissionPaymentFilter filter, List<Long> inProject) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (inProject != null) {
                predicates.add(inProject.isEmpty() ? cb.disjunction() : root.get("arrangementId").in(inProject));
            }
            if (filter.commissionId() != null) {
                predicates.add(cb.equal(root.get("arrangementId"), filter.commissionId()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("paymentDate"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("paymentDate"), filter.to()));
            }
            if (!Boolean.TRUE.equals(filter.includeVoided())) {
                predicates.add(cb.isNull(root.get("voidedAt")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    // same requestId but different details = client bug, don't hide it
    private CommissionPaymentResponse replay(CommissionPayment previous, CommissionArrangement arrangement, CommissionPaymentRequest request) {
        boolean same = previous.getArrangementId().equals(arrangement.getId())
                && previous.getAmount().compareTo(request.amount()) == 0
                && previous.getPaymentDate().equals(request.paymentDate())
                && previous.getPaymentMethodId().equals(request.paymentMethodId());
        if (!same) {
            throw new ConflictException("This request id was already used for a different payment.");
        }
        return mapper.toResponse(previous, arrangement, methodName(previous.getPaymentMethodId()));
    }

    private Map<Long, String> methodNames(Collection<CommissionPayment> payments) {
        return paymentMethods.findNames(payments.stream().map(CommissionPayment::getPaymentMethodId).collect(Collectors.toSet()));
    }

    private String methodName(long id) {
        return paymentMethods.findNames(List.of(id)).get(id);
    }

    // created=false when the requestId was already used
    public record PaymentResult(CommissionPaymentResponse payment, boolean created) {
    }
}
