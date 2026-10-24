package com.sondurukedella.expenses.repository;

import com.sondurukedella.expenses.entity.ExpensePayment;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExpensePaymentRepository extends JpaRepository<ExpensePayment, Long> {

    Optional<ExpensePayment> findByRequestId(UUID requestId);

    List<ExpensePayment> findByExpenseIdOrderByPaymentDateAscIdAsc(Long expenseId);

    // voided payments never count toward what was paid
    @Query("select coalesce(sum(p.amount), 0) from ExpensePayment p where p.expenseId = :expenseId and p.voidedAt is null")
    BigDecimal sumActive(Long expenseId);

    @Query("select p.expenseId as expenseId, sum(p.amount) as total from ExpensePayment p "
            + "where p.expenseId in :expenseIds and p.voidedAt is null group by p.expenseId")
    List<ExpenseTotal> sumActiveByExpense(Collection<Long> expenseIds);

    interface ExpenseTotal {
        Long getExpenseId();

        BigDecimal getTotal();
    }
}
