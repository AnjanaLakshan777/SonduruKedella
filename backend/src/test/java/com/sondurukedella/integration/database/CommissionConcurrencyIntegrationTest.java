package com.sondurukedella.integration.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.sondurukedella.commissions.dto.CommissionPaymentRequest;
import com.sondurukedella.commissions.dto.CommissionRowRequest;
import com.sondurukedella.commissions.dto.CommissionSetRequest;
import com.sondurukedella.commissions.dto.CommissionVoidRequest;
import com.sondurukedella.commissions.service.CommissionPaymentService;
import com.sondurukedella.commissions.service.CommissionService;
import com.sondurukedella.config.ClockConfig;
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

// Not @Transactional: real transactions racing each other. Everything belongs to project COMTEST-*
// and is deleted afterwards. Throwaway DB only.
@SpringBootTest
class CommissionConcurrencyIntegrationTest {

    @Autowired
    private CommissionService commissions;

    @Autowired
    private CommissionPaymentService payments;

    @Autowired
    private JdbcTemplate jdbc;

    private long cash;
    private long project;

    @BeforeEach
    void setUp() {
        cash = jdbc.queryForObject("SELECT id FROM payment_methods WHERE name = 'Cash'", Long.class);
        long client = jdbc.queryForObject("INSERT INTO clients (name, phone) VALUES ('COMTEST client', '0710000000') RETURNING id", Long.class);
        project = jdbc.queryForObject("""
                INSERT INTO projects (code, name, client_id, project_type, contract_value, start_date, expected_completion)
                VALUES (?, 'COMTEST', ?, 'DESIGN', 1000000, DATE '2026-01-01', DATE '2026-12-31') RETURNING id""",
                Long.class, "COMTEST-" + UUID.randomUUID().toString().substring(0, 8), client);
    }

    @AfterEach
    void cleanUp() {
        String ours = "SELECT id FROM projects WHERE code LIKE 'COMTEST-%'";
        String arrangements = "SELECT id FROM commission_arrangements WHERE project_id IN (" + ours + ")";
        // payments and confirmed arrangements refuse deletes (V1 / V6 triggers); lift them for this cleanup only
        jdbc.execute("ALTER TABLE commission_payments DISABLE TRIGGER trg_commission_payments_guard");
        jdbc.execute("ALTER TABLE commission_arrangements DISABLE TRIGGER trg_commission_arrangements_guard");
        try {
            jdbc.update("DELETE FROM commission_payments WHERE arrangement_id IN (" + arrangements + ")");
            jdbc.update("DELETE FROM commission_arrangements WHERE project_id IN (" + ours + ")");
        } finally {
            jdbc.execute("ALTER TABLE commission_payments ENABLE TRIGGER trg_commission_payments_guard");
            jdbc.execute("ALTER TABLE commission_arrangements ENABLE TRIGGER trg_commission_arrangements_guard");
        }
        jdbc.update("DELETE FROM activity_log WHERE project_id IN (" + ours + ")");
        jdbc.update("DELETE FROM projects WHERE code LIKE 'COMTEST-%'");
        jdbc.update("DELETE FROM clients WHERE name = 'COMTEST client'");
    }

    @Test
    void concurrentPaymentsNeverAddUpToMoreThanTheCommission() throws Exception {
        long id = confirm("12"); // 120,000
        LocalDate today = LocalDate.now(ClockConfig.BUSINESS_ZONE);
        // six payments of 30,000 at once: only four fit
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tasks.add(() -> payments.record(id, new CommissionPaymentRequest(UUID.randomUUID(), today, new BigDecimal("30000"), cash, null, null)));
        }
        List<Object> outcomes = race(tasks);
        assertThat(outcomes).filteredOn(o -> !(o instanceof Throwable)).hasSize(4);
        assertThat(jdbc.queryForObject("SELECT sum(amount)::text FROM commission_payments WHERE arrangement_id = ? AND voided_at IS NULL",
                String.class, id)).isEqualTo("120000.00");
    }

    @Test
    void theSamePaymentSentTwiceAtOnceIsRecordedOnce() throws Exception {
        long id = confirm("12");
        LocalDate today = LocalDate.now(ClockConfig.BUSINESS_ZONE);
        UUID request = UUID.randomUUID();
        Callable<Object> pay = () -> payments.record(id, new CommissionPaymentRequest(request, today, new BigDecimal("5000"), cash, null, null));
        race(List.of(pay, pay, pay));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commission_payments WHERE arrangement_id = ?", Long.class, id)).isOne();
    }

    @Test
    void aPaymentAndAVoidOfTheCommissionCannotBothWin() throws Exception {
        long id = confirm("12");
        long version = jdbc.queryForObject("SELECT version FROM commission_arrangements WHERE id = ?", Long.class, id);
        LocalDate today = LocalDate.now(ClockConfig.BUSINESS_ZONE);
        race(List.of(
                () -> payments.record(id, new CommissionPaymentRequest(UUID.randomUUID(), today, new BigDecimal("1000"), cash, null, null)),
                () -> commissions.voidArrangement(id, new CommissionVoidRequest("race", version))));
        String status = jdbc.queryForObject("SELECT status FROM commission_arrangements WHERE id = ?", String.class, id);
        long paid = jdbc.queryForObject("SELECT count(*) FROM commission_payments WHERE arrangement_id = ? AND voided_at IS NULL", Long.class, id);
        // either voided with nothing paid, or still confirmed with the payment
        assertThat(status.equals("VOIDED") ? paid == 0 : paid == 1).as(status + " / " + paid).isTrue();
    }

    @Test
    void twoSavesCreatingTheSameRecipientLeaveOneRow() throws Exception {
        CommissionSetRequest request = new CommissionSetRequest(true, null,
                List.of(new CommissionRowRequest(null, null, "Nimal", null, new BigDecimal("10"))));
        race(List.of(() -> commissions.saveSet(project, request), () -> commissions.saveSet(project, request)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commission_arrangements WHERE project_id = ?", Long.class, project)).isOne();
    }

    private long confirm(String rate) {
        return commissions.saveSet(project, new CommissionSetRequest(true, null,
                List.of(new CommissionRowRequest(null, null, "Nimal", null, new BigDecimal(rate))))).arrangements().getFirst().id();
    }

    // runs all at once and returns each result, or the exception it threw
    private static List<Object> race(List<Callable<Object>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return task.call();
                    } catch (RuntimeException ex) {
                        return ex;
                    }
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
