package com.sondurukedella.integration.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.sondurukedella.activity.service.ActivityService;
import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.projects.service.ProjectRevenueService;
import com.sondurukedella.revenue.dto.BillInstallmentRequest;
import com.sondurukedella.revenue.dto.ReceiptCreateRequest;
import com.sondurukedella.revenue.dto.WriteOffCreateRequest;
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

// Not @Transactional: these need real, separate transactions racing each other. Everything is
// created with code REVTEST-* and deleted afterwards. Throwaway DB only.
@SpringBootTest
class RevenueConcurrencyIntegrationTest {

    @Autowired
    private ProjectRevenueService revenue;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private ActivityService activityService;

    private long project;
    private long installment; // 100,000
    private long method;

    @BeforeEach
    void setUp() {
        long client = jdbc.queryForObject(
                "INSERT INTO clients (name, phone) VALUES ('RevTest Client', '0710000000') RETURNING id", Long.class);
        project = jdbc.queryForObject("""
                INSERT INTO projects (code, name, client_id, project_type, contract_value, start_date, expected_completion)
                VALUES ('REVTEST-1', 'RevTest project', ?, 'CONSTRUCTION', 100000, DATE '2026-01-01', DATE '2026-12-31')
                RETURNING id""", Long.class, client);
        installment = jdbc.queryForObject("""
                INSERT INTO installments (project_id, description, due_date, amount) VALUES (?, 'All', DATE '2026-12-31', 100000)
                RETURNING id""", Long.class, project);
        method = jdbc.queryForObject("SELECT id FROM payment_methods WHERE name = 'Cash'", Long.class);
    }

    @AfterEach
    void cleanUp() {
        String projects = "SELECT id FROM projects WHERE code LIKE 'REVTEST-%'";
        String installments = "SELECT id FROM installments WHERE project_id IN (" + projects + ")";
        jdbc.update("DELETE FROM activity_log WHERE project_id IN (" + projects + ")");
        // client_payments refuses deletes (V1 trigger); lift it for this table only, for test cleanup
        jdbc.execute("ALTER TABLE client_payments DISABLE TRIGGER trg_client_payments_guard");
        try {
            jdbc.update("DELETE FROM client_payments WHERE installment_id IN (" + installments + ")");
        } finally {
            jdbc.execute("ALTER TABLE client_payments ENABLE TRIGGER trg_client_payments_guard");
        }
        jdbc.update("DELETE FROM receivable_write_offs WHERE installment_id IN (" + installments + ")");
        jdbc.update("DELETE FROM installments WHERE project_id IN (" + projects + ")");
        jdbc.update("DELETE FROM projects WHERE code LIKE 'REVTEST-%'");
        jdbc.update("DELETE FROM clients WHERE name LIKE 'RevTest%'");
    }

    @Test
    void concurrentBillingSucceedsOnce() throws Exception {
        List<Outcome> outcomes = race(6, i -> () -> revenue.bill(project, installment, new BillInstallmentRequest(null, 0L)));

        assertThat(outcomes.stream().filter(o -> o.error == null)).hasSize(1);
        assertThat(outcomes.stream().filter(o -> o.error != null))
                .allMatch(o -> o.error instanceof ConflictException
                        || o.error instanceof org.springframework.orm.ObjectOptimisticLockingFailureException);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM activity_log WHERE project_id = ?", Long.class, project))
                .isEqualTo(1L);
    }

    @Test
    void concurrentReceiptsCannotOverpay() throws Exception {
        bill();
        // eight receipts of 20,000 against 100,000 owed: exactly five fit
        List<Outcome> outcomes = race(8, i -> () -> revenue.recordReceipt(project, installment, receipt(UUID.randomUUID(), "20000")));

        assertThat(outcomes.stream().filter(o -> o.error == null)).hasSize(5);
        assertThat(outcomes.stream().filter(o -> o.error != null)).hasSize(3)
                .allMatch(o -> o.error instanceof InvalidRequestException);
        assertThat(received()).isEqualByComparingTo("100000.00");
    }

    @Test
    void theSameSubmissionSentManyTimesAtOnceIsRecordedOnce() throws Exception {
        bill();
        UUID requestId = UUID.randomUUID();

        List<Outcome> outcomes = race(6, i -> () -> revenue.recordReceipt(project, installment, receipt(requestId, "30000")));

        assertThat(outcomes).allMatch(o -> o.error == null);
        assertThat(outcomes.stream().filter(o -> o.created)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM client_payments WHERE request_id = ?", Long.class, requestId))
                .isEqualTo(1L);
        assertThat(received()).isEqualByComparingTo("30000.00");
    }

    @Test
    void receiptsAndWriteOffsRacingForTheLastAmountCannotBothWin() throws Exception {
        bill();
        revenue.recordReceipt(project, installment, receipt(UUID.randomUUID(), "60000"));

        // 40,000 left; a 40,000 receipt and a 40,000 write-off at the same time
        List<Outcome> outcomes = race(2, i -> i == 0
                ? () -> revenue.recordReceipt(project, installment, receipt(UUID.randomUUID(), "40000"))
                : () -> revenue.writeOff(project, installment, new WriteOffCreateRequest(LocalDate.of(2026, 10, 20),
                        new BigDecimal("40000"), "Client went bankrupt in October.")));

        assertThat(outcomes.stream().filter(o -> o.error == null)).hasSize(1);
        BigDecimal writtenOff = jdbc.queryForObject(
                "SELECT coalesce(sum(amount), 0) FROM receivable_write_offs WHERE installment_id = ?", BigDecimal.class, installment);
        assertThat(received().add(writtenOff)).isEqualByComparingTo("100000.00");
    }

    @Test
    void aFailedActivityEntryRollsTheReceiptBack() {
        bill();
        ActivityService spy = AopTestUtils.getUltimateTargetObject(activityService);
        doThrow(new IllegalStateException("activity log down")).when(spy).record(any(), any(), any());

        assertThatThrownBy(() -> revenue.recordReceipt(project, installment, receipt(UUID.randomUUID(), "10000")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> revenue.writeOff(project, installment, new WriteOffCreateRequest(LocalDate.of(2026, 10, 20),
                new BigDecimal("10000"), "Client went bankrupt in October."))).isInstanceOf(IllegalStateException.class);

        assertThat(received()).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM receivable_write_offs WHERE installment_id = ?", Long.class,
                installment)).isZero();
    }

    private void bill() {
        revenue.bill(project, installment, new BillInstallmentRequest(LocalDate.of(2026, 10, 1), 0L));
    }

    private ReceiptCreateRequest receipt(UUID requestId, String amount) {
        return new ReceiptCreateRequest(requestId, LocalDate.of(2026, 10, 20), new BigDecimal(amount), method, null, null);
    }

    private BigDecimal received() {
        return jdbc.queryForObject("SELECT coalesce(sum(amount), 0) FROM client_payments "
                + "WHERE installment_id = ? AND voided_at IS NULL", BigDecimal.class, installment);
    }

    // starts every task at the same moment and collects how each one ended
    private static List<Outcome> race(int count, java.util.function.IntFunction<Callable<?>> task) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Callable<?> work = task.apply(i);
                futures.add(pool.submit(() -> {
                    go.await();
                    try {
                        Object result = work.call();
                        boolean created = !(result instanceof com.sondurukedella.revenue.service.ReceiptService.ReceiptResult r) || r.created();
                        return new Outcome(null, created);
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
