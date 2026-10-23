package com.sondurukedella.settings.entity;

import com.sondurukedella.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Expense category per work type. Locked categories (Materials, Labour, Commission) are seeded by V1
 * and cannot be removed; system-generated ones (Labour, Commission) cannot be used for manual expenses.
 */
@Getter
@Setter
@Entity
@Table(name = "expense_categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpenseCategory extends AuditableEntity {

    public static final int NAME_MAX_LENGTH = 100;

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "expense_group", nullable = false, length = 20)
    private ExpenseGroup expenseGroup;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private boolean locked;

    @Setter(AccessLevel.NONE)
    @Column(name = "system_generated", nullable = false)
    private boolean systemGenerated;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Version
    @Setter(AccessLevel.NONE)
    private Long version;

    /** Creates a user-defined category; locked and system-generated categories only come from V1. */
    public ExpenseCategory(ExpenseGroup expenseGroup, String name, int sortOrder) {
        this.expenseGroup = expenseGroup;
        this.name = name;
        this.sortOrder = sortOrder;
    }
}
