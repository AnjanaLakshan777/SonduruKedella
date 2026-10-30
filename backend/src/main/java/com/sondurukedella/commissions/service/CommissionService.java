package com.sondurukedella.commissions.service;

import com.sondurukedella.activity.entity.ActivityKind;
import com.sondurukedella.activity.service.ActivityService;
import com.sondurukedella.commissions.dto.CommissionDetail;
import com.sondurukedella.commissions.dto.CommissionFilter;
import com.sondurukedella.commissions.dto.CommissionPolicyCheck;
import com.sondurukedella.commissions.dto.CommissionResponse;
import com.sondurukedella.commissions.dto.CommissionRowRequest;
import com.sondurukedella.commissions.dto.CommissionSetRequest;
import com.sondurukedella.commissions.dto.CommissionState;
import com.sondurukedella.commissions.dto.CommissionSummaryResponse;
import com.sondurukedella.commissions.dto.CommissionTotals;
import com.sondurukedella.commissions.dto.CommissionVoidRequest;
import com.sondurukedella.commissions.dto.DesignSplit;
import com.sondurukedella.commissions.dto.ProjectCommissionsResponse;
import com.sondurukedella.commissions.dto.RecipientSummary;
import com.sondurukedella.commissions.entity.CommissionArrangement;
import com.sondurukedella.commissions.entity.CommissionStatus;
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
import com.sondurukedella.projects.dto.ProjectResponse;
import com.sondurukedella.projects.entity.ProjectType;
import com.sondurukedella.projects.service.ProjectService;
import com.sondurukedella.settings.dto.CommissionPolicyResponse;
import com.sondurukedella.settings.service.CommissionPolicyService;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Commissions on design and construction projects.
// Amount = contract value x rate. Drafts can change freely; confirming stores the amount and freezes
// the row. To fix a confirmed one, void it and add it again. Going over the design pool (or the
// construction level) is just a warning unless they confirm it as an exception.
@Service
@RequiredArgsConstructor
public class CommissionService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final Set<String> SORTABLE_PROPERTIES = Set.of("recipient", "project", "amount", "outstanding", "confirmedOn", "id");

    private final CommissionArrangementRepository repository;
    private final CommissionPaymentRepository payments;
    private final CommissionPaymentService paymentService;
    private final CommissionMapper mapper;
    private final ProjectService projectService;
    private final CommissionPolicyService policyService;
    private final ActivityService activityService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ProjectCommissionsResponse forProject(long projectId) {
        ProjectResponse project = projectService.get(projectId);
        return projectView(project, repository.findByProjectIdOrderByIdAsc(projectId));
    }

    // one transaction, project's rows locked first; the V6 unique index backs up the no-duplicates rule
    @Transactional
    public ProjectCommissionsResponse saveSet(long projectId, CommissionSetRequest request) {
        ProjectResponse project = projectService.get(projectId);
        if (project.type() == ProjectType.CONSULTATION) {
            throw new BusinessRuleException("Consultation projects have no commission.");
        }
        boolean confirm = request.confirm();
        if (confirm && request.arrangements().isEmpty()) {
            throw new InvalidRequestException("Add at least one recipient before confirming.");
        }

        List<CommissionArrangement> live = repository.lockLiveByProject(projectId, CommissionStatus.VOIDED);
        Map<Long, CommissionArrangement> liveById = live.stream().collect(Collectors.toMap(CommissionArrangement::getId, Function.identity()));
        Map<String, CommissionArrangement> liveByKey = live.stream()
                .collect(Collectors.toMap(a -> CommissionRules.key(a.getRecipientName(), a.getRole()), Function.identity()));

        List<Planned> plan = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        Set<Long> kept = new HashSet<>();
        for (CommissionRowRequest row : request.arrangements()) {
            String recipient = CommissionRules.cleanName(row.recipient());
            String role = CommissionRules.cleanRole(row.role());
            BigDecimal rate = CommissionCalculator.plain(row.ratePct());
            String key = CommissionRules.key(recipient, role);
            if (!keys.add(key)) {
                throw new InvalidRequestException(recipient + " is listed twice as " + role + ".");
            }
            CommissionArrangement target;
            if (row.id() != null) {
                target = liveById.get(row.id());
                if (target == null) {
                    throw new NotFoundException("Commission arrangement " + row.id() + " is not a live arrangement of this project.");
                }
                if (row.version() == null) {
                    throw new InvalidRequestException("Send the version of arrangement " + row.id() + ".");
                }
                ConflictException.requireCurrentVersion(row.version(), target.getVersion());
            } else {
                // same request sent twice - match it to the row the first one created
                target = liveByKey.get(key);
            }
            if (target != null) {
                if (!kept.add(target.getId())) {
                    throw new InvalidRequestException("Arrangement " + target.getId() + " is listed twice.");
                }
                if (target.isConfirmed() && !sameTerms(target, recipient, role, rate)) {
                    throw new BusinessRuleException(target.getRecipientName() + "'s commission is confirmed and can't be changed. "
                            + "Void it with a reason and add the corrected one.");
                }
            }
            plan.add(new Planned(target, recipient, role, rate));
        }

        List<CommissionArrangement> removed = live.stream().filter(a -> !kept.contains(a.getId())).toList();
        for (CommissionArrangement gone : removed) {
            if (gone.isConfirmed()) {
                throw new BusinessRuleException("The confirmed commission for " + gone.getRecipientName()
                        + " can't be removed. Void it with a reason instead.");
            }
        }

        CommissionPolicyResponse settings = policyService.get();
        CommissionPolicyCheck policy = CommissionCalculator.policy(project.type(),
                CommissionCalculator.totalRate(plan.stream().map(Planned::rate).toList()),
                settings.designCommissionPool(), settings.constructionDefaultRate());
        if (confirm && policy.exceeded() && !Boolean.TRUE.equals(request.acceptPolicyException())) {
            throw new BusinessRuleException(policy.warning() + " Nothing was changed. Adjust a percentage, or confirm it as an exception.");
        }

        // only drafts end up here, and drafts never have payments
        repository.deleteAll(removed);
        repository.flush();
        LocalDate today = LocalDate.now(clock);
        List<String> confirmedNow = new ArrayList<>();
        for (Planned planned : plan) {
            CommissionArrangement arrangement = planned.target();
            if (arrangement == null) {
                arrangement = new CommissionArrangement(projectId, project.type().name(), planned.recipient(), planned.role(), planned.rate());
            } else if (arrangement.isPending()) {
                arrangement.revise(planned.recipient(), planned.role(), planned.rate());
            }
            if (confirm && arrangement.isPending()) {
                arrangement.confirm(today, CommissionCalculator.amount(project.contractValue(), planned.rate()), policy.exceeded());
                confirmedNow.add(planned.recipient() + " " + planned.rate().toPlainString() + "%");
            }
            repository.save(arrangement);
        }
        repository.flush();

        if (confirm && !confirmedNow.isEmpty()) {
            activityService.record(projectId, ActivityKind.COMMISSION, "Commission arrangement confirmed (" + String.join(", ", confirmedNow) + ")"
                    + (policy.exceeded() ? " as a policy exception: " + policy.warning() : "."));
        } else if (!confirm) {
            activityService.record(projectId, ActivityKind.COMMISSION, "Commission arrangement saved as draft.");
        }
        return projectView(project, repository.findByProjectIdOrderByIdAsc(projectId));
    }

    // payments have to be voided first, so a voided commission never has money on it
    @Transactional
    public CommissionResponse voidArrangement(long id, CommissionVoidRequest request) {
        CommissionArrangement arrangement = repository.findByIdForUpdate(id).orElseThrow(CommissionService::notFound);
        if (arrangement.getStatus() == CommissionStatus.VOIDED) {
            throw new ConflictException("This commission was already voided.");
        }
        if (arrangement.isPending()) {
            throw new BusinessRuleException("A draft isn't a cost yet. Remove it from the project's commission list instead.");
        }
        ConflictException.requireCurrentVersion(request.version(), arrangement.getVersion());
        BigDecimal paid = payments.sumActive(id);
        if (paid.signum() > 0) {
            throw new BusinessRuleException("Void its payments first: " + Money.format(paid) + " is still recorded as paid.");
        }
        arrangement.markVoided(OffsetDateTime.now(clock), request.reason().strip());
        arrangement = repository.saveAndFlush(arrangement);
        activityService.record(arrangement.getProjectId(), ActivityKind.COMMISSION, "Commission of " + Money.format(arrangement.getCommissionAmount())
                + " for " + arrangement.getRecipientName() + " (" + CommissionCalculator.plain(arrangement.getRatePct()).toPlainString() + "%) voided: "
                + arrangement.getVoidReason());
        return mapper.toResponse(arrangement, projectService.get(arrangement.getProjectId()), BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public CommissionDetail get(long id) {
        CommissionArrangement arrangement = repository.findById(id).orElseThrow(CommissionService::notFound);
        ProjectResponse project = projectService.get(arrangement.getProjectId());
        return new CommissionDetail(mapper.toResponse(arrangement, project, payments.sumActive(id)),
                paymentService.forArrangement(arrangement));
    }

    // status and sorting need the paid totals, so that part is done in memory. Fine for a few hundred rows.
    @Transactional(readOnly = true)
    public PageResponse<CommissionResponse> search(CommissionFilter filter, Pageable pageable) {
        validate(filter, pageable);
        List<CommissionResponse> rows = rows(filter);
        rows = new ArrayList<>(rows);
        rows.sort(comparator(pageable.getSort()));
        int from = (int) Math.min((long) pageable.getPageNumber() * pageable.getPageSize(), rows.size());
        int to = Math.min(from + pageable.getPageSize(), rows.size());
        int totalPages = pageable.getPageSize() == 0 ? 0 : (rows.size() + pageable.getPageSize() - 1) / pageable.getPageSize();
        return new PageResponse<>(rows.subList(from, to), pageable.getPageNumber(), pageable.getPageSize(), rows.size(), totalPages);
    }

    @Transactional(readOnly = true)
    public CommissionSummaryResponse summary(ProjectType type) {
        List<CommissionResponse> rows = rows(new CommissionFilter(null, null, null, type, null, null, null));
        List<CommissionResponse> confirmed = rows.stream().filter(CommissionService::isCost).toList();
        List<CommissionResponse> pending = rows.stream().filter(r -> r.status() == CommissionState.PENDING).toList();
        long paymentCount = rows.isEmpty() ? 0 : payments.findByArrangementIdInOrderByPaymentDateAscIdAsc(
                rows.stream().map(CommissionResponse::id).toList()).stream().filter(p -> !p.isVoided()).count();
        return new CommissionSummaryResponse(
                sum(confirmed, CommissionResponse::amount),
                sum(confirmed, CommissionResponse::paid),
                sum(confirmed, CommissionResponse::outstanding),
                confirmed.size(),
                pending.size(),
                pending.stream().map(CommissionResponse::projectId).distinct().count(),
                rows.stream().filter(r -> r.status() == CommissionState.VOIDED).count(),
                paymentCount);
    }

    // most owed first
    @Transactional(readOnly = true)
    public List<RecipientSummary> recipients(ProjectType type) {
        List<CommissionResponse> confirmed = rows(new CommissionFilter(null, null, null, type, null, null, null)).stream()
                .filter(CommissionService::isCost)
                .sorted(Comparator.comparingLong(CommissionResponse::id))
                .toList();
        Map<String, List<CommissionResponse>> byRecipient = new LinkedHashMap<>();
        confirmed.forEach(r -> byRecipient.computeIfAbsent(CommissionRules.recipientKey(r.recipient()), k -> new ArrayList<>()).add(r));
        return byRecipient.values().stream()
                .map(list -> new RecipientSummary(list.getLast().recipient(), list.size(), sum(list, CommissionResponse::amount),
                        sum(list, CommissionResponse::paid), sum(list, CommissionResponse::outstanding)))
                .sorted(Comparator.comparing(RecipientSummary::outstanding).reversed().thenComparing(RecipientSummary::recipient))
                .toList();
    }

    private ProjectCommissionsResponse projectView(ProjectResponse project, List<CommissionArrangement> arrangements) {
        Map<Long, BigDecimal> paid = paidByArrangement(arrangements);
        List<CommissionResponse> rows = arrangements.stream()
                .map(a -> mapper.toResponse(a, project, paid.getOrDefault(a.getId(), BigDecimal.ZERO)))
                .toList();
        List<CommissionResponse> live = rows.stream().filter(r -> r.status() != CommissionState.VOIDED).toList();
        CommissionPolicyResponse settings = policyService.get();
        CommissionPolicyCheck policy = CommissionCalculator.policy(project.type(),
                CommissionCalculator.totalRate(live.stream().map(CommissionResponse::ratePct).toList()),
                settings.designCommissionPool(), settings.constructionDefaultRate());
        DesignSplit split = project.type() == ProjectType.DESIGN
                ? CommissionCalculator.designSplit(project.contractValue(), settings.designCommissionPool(), sum(live, CommissionResponse::amount))
                : null;
        List<CommissionResponse> confirmed = rows.stream().filter(CommissionService::isCost).toList();
        CommissionTotals totals = new CommissionTotals(sum(confirmed, CommissionResponse::amount), sum(confirmed, CommissionResponse::paid),
                sum(confirmed, CommissionResponse::outstanding),
                sum(rows.stream().filter(r -> r.status() == CommissionState.PENDING).toList(), CommissionResponse::amount));
        return new ProjectCommissionsResponse(project.id(), project.code(), project.name(), project.type(), project.contractValue(),
                policy, split, totals, rows);
    }

    private List<CommissionResponse> rows(CommissionFilter filter) {
        List<CommissionArrangement> arrangements = repository.findAll(matching(filter), Sort.by("id"));
        Map<Long, BigDecimal> paid = paidByArrangement(arrangements);
        Map<Long, ProjectResponse> projects = new HashMap<>();
        List<CommissionResponse> rows = arrangements.stream()
                .map(a -> mapper.toResponse(a, projects.computeIfAbsent(a.getProjectId(), projectService::get),
                        paid.getOrDefault(a.getId(), BigDecimal.ZERO)))
                .toList();
        if (filter.status() != null) {
            rows = rows.stream().filter(r -> r.status() == filter.status()).toList();
        }
        return rows;
    }

    private Map<Long, BigDecimal> paidByArrangement(Collection<CommissionArrangement> arrangements) {
        if (arrangements.isEmpty()) {
            return Map.of();
        }
        return payments.sumActiveByArrangement(arrangements.stream().map(CommissionArrangement::getId).toList()).stream()
                .collect(Collectors.toMap(CommissionPaymentRepository.ArrangementTotal::getArrangementId,
                        CommissionPaymentRepository.ArrangementTotal::getTotal));
    }

    private static Specification<CommissionArrangement> matching(CommissionFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.projectId() != null) {
                predicates.add(cb.equal(root.get("projectId"), filter.projectId()));
            }
            if (filter.type() != null) {
                predicates.add(cb.equal(root.get("projectType"), filter.type().name()));
            }
            if (filter.status() != null) {
                // paid / partly paid get filtered after we have the totals
                CommissionStatus stored = switch (filter.status()) {
                    case PENDING -> CommissionStatus.PENDING;
                    case VOIDED -> CommissionStatus.VOIDED;
                    case CONFIRMED, PARTIALLY_PAID, PAID -> CommissionStatus.CONFIRMED;
                };
                predicates.add(cb.equal(root.get("status"), stored));
            }
            if (filter.recipient() != null && !filter.recipient().isBlank()) {
                // names are stored with spaces collapsed, so lower() is enough
                predicates.add(cb.equal(cb.lower(root.get("recipientName")), CommissionRules.recipientKey(filter.recipient())));
            }
            if (filter.q() != null && !filter.q().isBlank()) {
                String pattern = "%" + escapeLike(filter.q().strip().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("recipientName")), pattern, '\\'),
                        cb.like(cb.lower(root.get("role")), pattern, '\\')));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("confirmedOn"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("confirmedOn"), filter.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void validate(CommissionFilter filter, Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("The page size can be at most " + MAX_PAGE_SIZE + ".");
        }
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidRequestException("Commissions cannot be sorted by \"" + order.getProperty()
                        + "\". Use one of: recipient, project, amount, outstanding, confirmedOn, id.");
            }
        }
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new InvalidRequestException("The start date must be on or before the end date.");
        }
    }

    // default: most outstanding first, id as tie-breaker
    private static Comparator<CommissionResponse> comparator(Sort sort) {
        Comparator<CommissionResponse> result = null;
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Direction.DESC, "outstanding");
        }
        for (Sort.Order order : sort) {
            Comparator<CommissionResponse> next = switch (order.getProperty()) {
                case "recipient" -> Comparator.comparing((CommissionResponse r) -> r.recipient().toLowerCase(Locale.ROOT));
                case "project" -> Comparator.comparing(CommissionResponse::projectCode);
                case "amount" -> Comparator.comparing(CommissionResponse::amount);
                case "outstanding" -> Comparator.comparing(CommissionResponse::outstanding);
                case "confirmedOn" -> Comparator.comparing(CommissionResponse::confirmedOn, Comparator.nullsLast(Comparator.naturalOrder()));
                default -> Comparator.comparingLong(CommissionResponse::id);
            };
            if (order.isDescending()) {
                next = next.reversed();
            }
            result = result == null ? next : result.thenComparing(next);
        }
        return result.thenComparingLong(CommissionResponse::id);
    }

    private static boolean sameTerms(CommissionArrangement arrangement, String recipient, String role, BigDecimal rate) {
        return arrangement.getRecipientName().equals(recipient) && arrangement.getRole().equals(role)
                && arrangement.getRatePct().compareTo(rate) == 0;
    }

    private static boolean isCost(CommissionResponse row) {
        return row.status() == CommissionState.CONFIRMED || row.status() == CommissionState.PARTIALLY_PAID
                || row.status() == CommissionState.PAID;
    }

    private static BigDecimal sum(List<CommissionResponse> rows, Function<CommissionResponse, BigDecimal> value) {
        return Money.amount(rows.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    // so % and _ typed in search are matched literally
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    static NotFoundException notFound() {
        return new NotFoundException("Commission not found.");
    }

    private record Planned(CommissionArrangement target, String recipient, String role, BigDecimal rate) {
    }
}
