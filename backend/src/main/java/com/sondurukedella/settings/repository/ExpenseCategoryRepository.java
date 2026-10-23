package com.sondurukedella.settings.repository;

import com.sondurukedella.settings.entity.ExpenseCategory;
import com.sondurukedella.settings.entity.ExpenseGroup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    List<ExpenseCategory> findByActiveTrueOrderByExpenseGroupAscSortOrderAscIdAsc();

    List<ExpenseCategory> findByExpenseGroupAndActiveTrueOrderBySortOrderAscIdAsc(ExpenseGroup expenseGroup);

    Optional<ExpenseCategory> findByExpenseGroupAndNameIgnoreCase(ExpenseGroup expenseGroup, String name);

    @Query("select coalesce(max(c.sortOrder), 0) from ExpenseCategory c where c.expenseGroup = :group")
    int findMaxSortOrder(ExpenseGroup group);
}
