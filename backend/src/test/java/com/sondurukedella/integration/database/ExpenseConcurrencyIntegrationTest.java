package com.sondurukedella.integration.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.sondurukedella.activity.service.ActivityService;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.expenses.dto.ExpenseCreateRequest;
import com.sondurukedella.expenses.dto.ExpensePaymentRequest;
import com.sondurukedella.expenses.entity.ExpenseScope;
import com.sondurukedella.expenses.service.ExpensePaymentService;
import com.sondurukedella.expenses.service.ExpensePaymentService.PaymentResult;
import com.sondurukedella.expenses.service.ExpenseService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

// Not @Transactional: real, separate transactions racing each other. Everything here is a company
// expense paid to "ExpTest ..." and is deleted afterwards. Throwaway DB only.
@SpringBootTest
class ExpenseConcurrencyIntegrationTest {

    private static final String PAYEE = "ExpTest Supplier";

    @Autowired
    private ExpenseService expenses;

    @Autowired
    private ExpensePaymentService payments;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private ActivityService activityService;

    private long marketing;
    private long cash;

    @BeforeEach
    void setUp() {
        marketing = jdbc.queryForObject("SELECT id FROM expense_categories WHERE expense_group = 'COMPANY' AND name = 'Marketing'", Long.class);
        cash = jdbc.queryForObject("SELECT id FROM payment_methods WHERE name = 'Cash'", Long.class);
    }

    @AfterEach
    void cleanUp() {
        String ours = "SELECT id FROM expenses WHERE payee = '" + PAYEE + "'";
        jdbc.update("DELETE FROM activity_log WHERE project_id IS NULL AND message LIKE '%" + PAYEE + "%' OR message LIKE '%ExpTest banner%'");
        // expense_payments refuses deletes (V1 trigger); lift it for this table only, for test cleanup
        jdbc.execute("ALTER TABLE expense_payments DISABLE TRIGGER trg_expense_payments_guard");
        try {
            jdbc.update("DELETE FROM expense_payments WHERE expense_id IN (" + ours + ")");
        } finally {
            jdbc.execute("ALTER TABLE expense_payments ENABLE TRIGGER trg_expense_payments_guard");
        }
        jdbc.update("DELETE FROM expenses WHERE payee = '" + PAYEE + "'");
    }

    @Test
    void concurrentPaymentsCannotOverpay() throws Exception {
        long id = expenses.create(expense("100000", null)).id();

        // eight payments of 20,000 against 100,000: exactly five fit
        List<Outcome> outcomes = race(8, () -> payments.record(id, payment(UUID.randomUUID(), "20000")));

        assertThat(outcomes.stream().filter(o -> o.error == null)).hasSize(5);
        assertThat(outcomes.stream().filter(o -> o.error != null)).hasSize(3).allMatch(o -> o.error instanceof InvalidRequestException);
        assertThat(paid(id)).isEqualByComparingTo("100000.00");
    }

    @Test
    void theSameSubmissionSentManyTimesAtOncePaysOnce() throws Exception {
        long id = expenses.create(expense("100000", null)).id();
        UUID requestId = UUID.randomUUID();

        List<Outcome> outcomes = race(6, () -> payments.record(id, payment(requestId, "25000")));

        assertThat(outcomes).allMatch(o -> o.error == null);
        assertThat(outcomes.stream().filter(o -> o.created)).hasSize(1);
        assertThat(paid(id)).isEqualByComparingTo("25000.00");
    }

    @Test
    void aFailureAfterTheExpenseIsWrittenRollsTheExpenseAndItsPaymentBack() {
        // the activity entry for the payment is the last write of the create transaction
        ActivityService spy = AopTestUtils.getUltimateTargetObject(activityService);
        doThrow(new IllegalStateException("activity log down")).when(spy).record(any(), any(),
                org.mockito.ArgumentMatchers.startsWith("Expense payment"));

        assertThatThrownBy(() -> expenses.create(expense("5000", payment(UUID.randomUUID(), "5000"))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses WHERE payee = ?", Long.class, PAYEE)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expense_payments p JOIN expenses e ON e.id = p.expense_id "
                + "WHERE e.payee = ?", Long.class, PAYEE)).isZero();
    }

    private ExpenseCreateRequest expense(String amount, ExpensePaymentRequest initial) {
        return new ExpenseCreateRequest(LocalDate.of(2026, 10, 20), ExpenseScope.COMPANY, null, marketing, "ExpTest banner",
                PAYEE, null, new BigDecimal(amount), null, null, null, null, null, null, null, null, null, null, initial);
    }

    private ExpensePaymentRequest payment(UUID requestId, String amount) {
        return new ExpensePaymentRequest(requestId, LocalDate.of(2026, 10, 21), new BigDecimal(amount), cash, null, null);
    }

    private BigDecimal paid(long id) {
        return jdbc.queryForObject("SELECT coalesce(sum(amount), 0) FROM expense_payments WHERE expense_id = ? AND voided_at IS NULL",
                BigDecimal.class, id);
    }

    // starts every task at the same moment and records how each ended
    private static List<Outcome> race(int count, Callable<PaymentResult> task) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    try {
                        return new Outcome(null, task.call().created());
                    } catch (Exception ex) {
                        return new Outcome(ex, false);
                    }
                }));
            }
            go.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private record Outcome(Exception error, boolean created) {
    }
}
