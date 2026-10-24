package com.sondurukedella.expenses.service;

import com.sondurukedella.activity.entity.ActivityKind;
import com.sondurukedella.activity.service.ActivityService;
import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.expenses.dto.ExpenseCreateRequest;
import com.sondurukedella.expenses.dto.ExpenseDetail;
import com.sondurukedella.expenses.dto.ExpenseFilter;
import com.sondurukedella.expenses.dto.ExpenseInput;
import com.sondurukedella.expenses.dto.ExpensePaymentStatus;
import com.sondurukedella.expenses.dto.ExpenseResponse;
import com.sondurukedella.expenses.dto.ExpenseSummaryResponse;
import com.sondurukedella.expenses.dto.ExpenseUpdateRequest;
import com.sondurukedella.expenses.entity.Expense;
import com.sondurukedella.expenses.entity.ExpenseScope;
import com.sondurukedella.expenses.mapper.ExpenseMapper;
import com.sondurukedella.expenses.repository.ExpensePaymentRepository;
import com.sondurukedella.expenses.repository.ExpensePaymentRepository.ExpenseTotal;
import com.sondurukedella.expenses.repository.ExpenseRepository;
import com.sondurukedella.expenses.repository.ExpenseSpecifications;
import com.sondurukedella.expenses.service.ExpenseCalculator.Balance;
import com.sondurukedella.expenses.validation.ExpenseRules;
import com.sondurukedella.projects.dto.ProjectResponse;
import com.sondurukedella.projects.entity.ProjectStatus;
import com.sondurukedella.projects.service.ProjectService;
import com.sondurukedella.settings.dto.ExpenseCategoryResponse;
import com.sondurukedella.settings.entity.ExpenseGroup;
import com.sondurukedella.settings.service.ExpenseCategoryService;
import com.sondurukedella.settings.service.MeasurementUnitService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Manual project and company expenses. Projects, categories and units are checked through their
// owners' services (ProjectService, ExpenseCategoryService, MeasurementUnitService), never their
// repositories. Expenses are never deleted; corrections are edits (with optimistic locking) or voids.
@Service
@RequiredArgsConstructor
public class ExpenseService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final Set<String> SORTABLE_PROPERTIES = Set.of("expenseDate", "amount", "dueDate", "createdAt", "id");

    private final ExpenseRepository repository;
    private final ExpensePaymentRepository payments;
    private final ExpensePaymentService paymentService;
    private final ExpenseMapper mapper;
    private final ProjectService projectService;
    private final ExpenseCategoryService categories;
    private final MeasurementUnitService units;
    private final ActivityService activityService;
    private final Clock clock;

    // The expense and the optional first payment commit together, as the form's single Save expects.
    @Transactional
    public ExpenseResponse create(ExpenseCreateRequest request) {
        Resolved resolved = resolve(request, null);
        Expense expense = new Expense();
        apply(expense, request, resolved);
        expense = repository.saveAndFlush(expense);
        activityService.record(expense.getProjectId(), ActivityKind.EXPENSE, "Expense recorded: "
                + resolved.category().name() + " - " + expense.getDescription() + " (" + Money.format(expense.getAmount()) + ").");
        if (request.initialPayment() != null) {
            paymentService.recordOn(expense, request.initialPayment());
        }
        return toResponse(expense);
    }

    // A full edit, as the edit dialog allows. The row is locked so a payment can't land between the
    // "already paid" check and the new amount.
    @Transactional
    public ExpenseResponse update(long id, ExpenseUpdateRequest request) {
        Expense expense = repository.findByIdForUpdate(id).orElseThrow(ExpensePaymentService::expenseNotFound);
        ConflictException.requireCurrentVersion(request.version(), expense.getVersion());
        Resolved resolved = resolve(request, expense);
        BigDecimal paid = payments.sumActive(id);
        if (resolved.amount().compareTo(paid) < 0) {
            // lowering it below what was already paid would need a payment voided first
            throw new InvalidRequestException(Money.format(paid) + " has already been paid on this expense; "
                    + "the amount cannot be less than that. Void a payment first if it was wrong.");
        }
        Long previousProject = expense.getProjectId();
        apply(expense, request, resolved);
        expense = repository.saveAndFlush(expense);

        String message = "Expense updated: " + resolved.category().name() + " - " + expense.getDescription()
                + " (" + Money.format(expense.getAmount()) + ").";
        activityService.record(expense.getProjectId(), ActivityKind.EXPENSE, message);
        // moved between projects (or to company level): the old project's feed should say so too
        if (previousProject != null && !previousProject.equals(expense.getProjectId())) {
            activityService.record(previousProject, ActivityKind.EXPENSE, "Expense moved off this project: "
                    + expense.getDescription() + " (" + Money.format(expense.getAmount()) + ").");
        }
        return toResponse(expense);
    }

    @Transactional(readOnly = true)
    public ExpenseDetail get(long id) {
        Expense expense = repository.findById(id).orElseThrow(ExpensePaymentService::expenseNotFound);
        return new ExpenseDetail(toResponse(expense), paymentService.list(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> search(ExpenseFilter filter, Pageable pageable) {
        validate(filter);
        Page<Expense> page = repository.findAll(ExpenseSpecifications.matching(filter, today()), stablePage(pageable));
        List<ExpenseResponse> rows = toResponses(page.getContent());
        return new PageResponse<>(rows, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    // Totals for the same filter as the list, over all matching rows (not just one page).
    @Transactional(readOnly = true)
    public ExpenseSummaryResponse summary(ExpenseFilter filter) {
        validate(filter);
        List<ExpenseResponse> rows = toResponses(repository.findAll(ExpenseSpecifications.matching(filter, today())));
        return new ExpenseSummaryResponse(
                rows.size(),
                sum(rows, ExpenseResponse::amount),
                sum(rows.stream().filter(r -> r.scope() == ExpenseScope.PROJECT).toList(), ExpenseResponse::amount),
                sum(rows.stream().filter(r -> r.scope() == ExpenseScope.COMPANY).toList(), ExpenseResponse::amount),
                sum(rows, ExpenseResponse::paid),
                sum(rows, ExpenseResponse::outstanding),
                sum(rows.stream().filter(r -> r.status() == ExpensePaymentStatus.OVERDUE).toList(), ExpenseResponse::outstanding));
    }

    // What the project, category and unit checks found, so apply() doesn't look them up again.
    private record Resolved(ExpenseCategoryResponse category, ExpenseGroup group, String projectType, BigDecimal amount) {
    }

    // existing is null on create. On edit, a category, unit or cancelled project the expense already
    // had is accepted even though it couldn't be chosen today - the history keeps what it was.
    private Resolved resolve(ExpenseInput input, Expense existing) {
        ExpenseRules.validateScope(input);
        ExpenseRules.validatePeriod(input);
        BigDecimal amount = ExpenseRules.amount(input);

        ExpenseGroup group = ExpenseGroup.COMPANY;
        String projectType = null;
        if (input.scope() == ExpenseScope.PROJECT) {
            ProjectResponse project = project(input.projectId());
            boolean unchanged = existing != null && input.projectId().equals(existing.getProjectId());
            // the expense dialog doesn't offer cancelled projects for new costs
            if (!unchanged && project.status() == ProjectStatus.CANCELLED) {
                throw new InvalidRequestException("Project " + project.code() + " is cancelled; new expenses can't be added to it.");
            }
            group = ExpenseGroup.valueOf(project.type().name());
            projectType = project.type().name();
        }

        ExpenseCategoryResponse category = categories.findById(input.categoryId())
                .orElseThrow(() -> new InvalidRequestException("Choose a category from the Settings list."));
        // Labour and Commission are worked out by Payroll and Commissions; a manual row would count them twice
        if (category.systemGenerated()) {
            throw new InvalidRequestException(category.name() + " costs are added automatically by "
                    + "Labour & Payroll or Commissions and cannot be entered as an expense.");
        }
        if (category.group() != group) {
            throw new InvalidRequestException("\"" + category.name() + "\" is a " + label(category.group())
                    + " category; this expense needs a " + label(group) + " category.");
        }
        boolean sameCategory = existing != null && category.id() == existing.getCategoryId();
        if (!category.active() && !sameCategory) {
            throw new InvalidRequestException("\"" + category.name() + "\" has been removed from the Settings list.");
        }

        if (input.unitId() != null) {
            boolean sameUnit = existing != null && input.unitId().equals(existing.getUnitId());
            if (!sameUnit && !units.isActive(input.unitId())) {
                throw new InvalidRequestException("Choose a unit from the Settings list.");
            }
        }
        return new Resolved(category, group, projectType, amount);
    }

    private ProjectResponse project(long projectId) {
        try {
            return projectService.get(projectId);
        } catch (NotFoundException ex) {
            // the project is part of the request body, so this is bad input rather than a missing URL
            throw new InvalidRequestException("The selected project does not exist.");
        }
    }

    private static void apply(Expense expense, ExpenseInput input, Resolved resolved) {
        expense.setExpenseDate(input.date());
        expense.setScope(input.scope());
        expense.setProjectId(input.scope() == ExpenseScope.PROJECT ? input.projectId() : null);
        expense.setProjectType(resolved.projectType());
        expense.setCategoryId(resolved.category().id());
        expense.setExpenseGroup(resolved.group());
        expense.setCategorySystemGenerated(false);
        expense.setDescription(input.description().strip());
        expense.setPayee(input.payee().strip());
        expense.setAmount(resolved.amount());
        expense.setDueDate(input.dueDate());
        expense.setInvoiceNo(ExpenseRules.trimToNull(input.invoiceNo()));
        expense.setNotes(ExpenseRules.trimToNull(input.notes()));
        expense.setQuantity(input.quantity());
        expense.setUnitId(input.unitId());
        expense.setUnitPrice(input.unitPrice());
        expense.setMachineType(ExpenseRules.trimToNull(input.machineType()));
        expense.setUsageFrom(input.usageFrom());
        expense.setUsageTo(input.usageTo());
        expense.setBillingPeriod(ExpenseRules.trimToNull(input.billingPeriod()));
    }

    private ExpenseResponse toResponse(Expense expense) {
        return toResponses(List.of(expense)).getFirst();
    }

    // one query each for payments, category names and unit names, however many rows
    private List<ExpenseResponse> toResponses(List<Expense> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> paid = payments.sumActiveByExpense(list.stream().map(Expense::getId).toList()).stream()
                .collect(Collectors.toMap(ExpenseTotal::getExpenseId, ExpenseTotal::getTotal));
        Map<Long, String> categoryNames = categoryNames(list.stream().map(Expense::getCategoryId).collect(Collectors.toSet()));
        Map<Long, String> unitNames = units.findNames(
                list.stream().map(Expense::getUnitId).filter(Objects::nonNull).collect(Collectors.toSet()));
        LocalDate today = today();
        return list.stream().map(e -> {
            Balance balance = ExpenseCalculator.of(e.getAmount(), paid.getOrDefault(e.getId(), BigDecimal.ZERO), e.getDueDate(), today);
            return mapper.toResponse(e, categoryNames.get(e.getCategoryId()),
                    e.getUnitId() == null ? null : unitNames.get(e.getUnitId()), balance);
        }).toList();
    }

    private Map<Long, String> categoryNames(Collection<Long> ids) {
        return ids.stream().collect(Collectors.toMap(id -> id,
                id -> categories.findById(id).map(ExpenseCategoryResponse::name).orElse(null)));
    }

    private static void validate(ExpenseFilter filter) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new InvalidRequestException("from must be on or before to.");
        }
        if (filter.scope() == ExpenseScope.COMPANY && filter.projectId() != null) {
            throw new InvalidRequestException("Company-level expenses have no project; drop projectId or the scope.");
        }
    }

    // newest first by default, with id breaking ties so paging is stable
    private static Pageable stablePage(Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("The page size can be at most " + MAX_PAGE_SIZE + ".");
        }
        Sort sort = pageable.getSort();
        for (Sort.Order order : sort) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidRequestException("Expenses cannot be sorted by \"" + order.getProperty()
                        + "\". Use one of: expenseDate, amount, dueDate, createdAt, id.");
            }
        }
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Order.desc("expenseDate"));
        }
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by(Sort.Order.desc("id")));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static String label(ExpenseGroup group) {
        return group.name().charAt(0) + group.name().substring(1).toLowerCase(Locale.ROOT);
    }

    private static BigDecimal sum(List<ExpenseResponse> rows, Function<ExpenseResponse, BigDecimal> field) {
        return Money.amount(rows.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
