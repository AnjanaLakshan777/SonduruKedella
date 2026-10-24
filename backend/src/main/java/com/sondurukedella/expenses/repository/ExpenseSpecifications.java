package com.sondurukedella.expenses.repository;

import com.sondurukedella.expenses.dto.ExpenseFilter;
import com.sondurukedella.expenses.entity.Expense;
import com.sondurukedella.expenses.entity.ExpensePayment;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class ExpenseSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private ExpenseSpecifications() {
    }

    // today is passed in (Colombo date from the Clock) because the OVERDUE filter depends on it
    public static Specification<Expense> matching(ExpenseFilter filter, LocalDate today) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.scope() != null) {
                predicates.add(cb.equal(root.get("scope"), filter.scope()));
            }
            if (filter.projectId() != null) {
                predicates.add(cb.equal(root.get("projectId"), filter.projectId()));
            }
            if (filter.categoryId() != null) {
                predicates.add(cb.equal(root.get("categoryId"), filter.categoryId()));
            }
            if (filter.group() != null) {
                predicates.add(cb.equal(root.get("expenseGroup"), filter.group()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("expenseDate"), filter.to()));
            }
            if (filter.q() != null && !filter.q().isBlank()) {
                String pattern = "%" + escapeLike(filter.q().strip().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("description")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("payee")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("invoiceNo")), pattern, LIKE_ESCAPE)));
            }
            if (filter.status() != null) {
                Subquery<BigDecimal> paidQuery = query.subquery(BigDecimal.class);
                Root<ExpensePayment> payment = paidQuery.from(ExpensePayment.class);
                paidQuery.select(cb.coalesce(cb.sum(payment.<BigDecimal>get("amount")), BigDecimal.ZERO))
                        .where(cb.equal(payment.get("expenseId"), root.get("id")), cb.isNull(payment.get("voidedAt")));
                predicates.add(status(filter, root, cb, paidQuery, today));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    // Mirrors ExpenseCalculator, so filtering by status gives the same answer as reading it.
    private static Predicate status(ExpenseFilter filter, Root<Expense> root, CriteriaBuilder cb,
            Expression<BigDecimal> paid, LocalDate today) {
        Expression<BigDecimal> amount = root.get("amount");
        Predicate settled = cb.greaterThanOrEqualTo(paid, amount);
        Predicate overdue = cb.and(cb.isNotNull(root.get("dueDate")), cb.lessThan(root.get("dueDate"), today));
        return switch (filter.status()) {
            case PAID -> settled;
            case OVERDUE -> cb.and(cb.not(settled), overdue);
            case PARTIALLY_PAID -> cb.and(cb.not(settled), cb.not(overdue), cb.greaterThan(paid, BigDecimal.ZERO));
            case UNPAID -> cb.and(cb.not(overdue), cb.equal(paid, BigDecimal.ZERO));
        };
    }

    // so "%" or "_" typed in the search box are matched literally
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
