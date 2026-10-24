package com.sondurukedella.integration.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.sondurukedella.activity.service.ActivityService;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.inventory.dto.MaterialCreateRequest;
import com.sondurukedella.inventory.dto.StockIssueRequest;
import com.sondurukedella.inventory.dto.StockReceiptRequest;
import com.sondurukedella.inventory.dto.StockReturnRequest;
import com.sondurukedella.inventory.service.MaterialService;
import com.sondurukedella.inventory.service.StockMovementService;
import com.sondurukedella.inventory.service.StockMovementService.MovementResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

// Not @Transactional: real transactions racing each other (FIFO layers and allocations included). Everything uses SKUs INVTEST-* and project
// code INVTEST-1 and is deleted afterwards. Throwaway DB only.
@SpringBootTest
class InventoryConcurrencyIntegrationTest {

    @Autowired
    private MaterialService materials;

    @Autowired
    private StockMovementService stock;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private ActivityService activityService;

    private long project;
    private long material;

    @BeforeEach
    void setUp() {
        long client = jdbc.queryForObject("INSERT INTO clients (name, phone) VALUES ('InvTest Client', '0710000000') RETURNING id", Long.class);
        project = jdbc.queryForObject("""
                INSERT INTO projects (code, name, client_id, project_type, contract_value, start_date, expected_completion)
                VALUES ('INVTEST-1', 'InvTest project', ?, 'CONSTRUCTION', 1000000, DATE '2026-01-01', DATE '2026-12-31')
                RETURNING id""", Long.class, client);
        long bag = jdbc.queryForObject("SELECT id FROM units WHERE name = 'bag'", Long.class);
        material = materials.create(new MaterialCreateRequest("INVTEST-" + UUID.randomUUID().toString().substring(0, 8),
                "InvTest Cement", "Building", bag, null)).id();
    }

    @AfterEach
    void cleanUp() {
        String ours = "SELECT id FROM materials WHERE sku LIKE 'INVTEST-%'";
        jdbc.update("DELETE FROM activity_log WHERE project_id IN (SELECT id FROM projects WHERE code = 'INVTEST-1')");
        // the ledger and the allocations refuse deletes (V1, V4 triggers); lift them for test cleanup only
        jdbc.execute("ALTER TABLE stock_cost_allocations DISABLE TRIGGER trg_stock_cost_allocations_immutable");
        jdbc.execute("ALTER TABLE stock_movements DISABLE TRIGGER trg_stock_movements_immutable");
        try {
            jdbc.update("DELETE FROM stock_cost_allocations WHERE material_id IN (" + ours + ")");
            jdbc.update("DELETE FROM stock_cost_layers WHERE material_id IN (" + ours + ")");
            jdbc.update("DELETE FROM stock_movements WHERE material_id IN (" + ours + ") AND returned_from_movement_id IS NOT NULL");
            jdbc.update("DELETE FROM stock_movements WHERE material_id IN (" + ours + ")");
        } finally {
            jdbc.execute("ALTER TABLE stock_movements ENABLE TRIGGER trg_stock_movements_immutable");
            jdbc.execute("ALTER TABLE stock_cost_allocations ENABLE TRIGGER trg_stock_cost_allocations_immutable");
        }
        jdbc.update("DELETE FROM materials WHERE sku LIKE 'INVTEST-%'");
        jdbc.update("DELETE FROM projects WHERE code = 'INVTEST-1'");
        jdbc.update("DELETE FROM clients WHERE name = 'InvTest Client'");
    }

    @Test
    void concurrentIssuesCannotSpendTheSameLayerTwice() throws Exception {
        receive("50", "2000");
        receive("50", "3000");

        // eight issues of 20 bags against 100 in stock: exactly five fit
        List<Outcome> outcomes = race(8, i -> () -> stock.issue(issue(UUID.randomUUID(), "20")));

        assertThat(outcomes.stream().filter(o -> o.error == null)).hasSize(5);
        assertThat(outcomes.stream().filter(o -> o.error != null)).hasSize(3).allMatch(o -> o.error instanceof InvalidRequestException);
        assertThat(balance().get("q")).isEqualTo(new BigDecimal("0.000"));
        assertThat(balance().get("v")).isEqualTo(new BigDecimal("0.00"));
        // whatever order they won the lock in, together they took each layer exactly once: 100,000 + 150,000
        assertThat(jdbc.queryForObject("SELECT -sum(total_cost) FROM stock_movements WHERE material_id = ? AND movement_type = 'ISSUED'",
                BigDecimal.class, material)).isEqualByComparingTo("250000.00");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM stock_cost_layers l
                 WHERE l.material_id = ? AND l.original_quantity <> -(SELECT sum(quantity) FROM stock_cost_allocations a WHERE a.layer_id = l.id)""",
                Long.class, material)).isZero();
        assertReconciles();
    }

    @Test
    void concurrentReceiptsQueueAsLayersInPostingOrder() throws Exception {
        // ten receipts of 10 bags at 2,500, 2,600 ... 3,400, all at once
        List<Outcome> outcomes = race(10, i -> () -> stock.receive(receipt(UUID.randomUUID(), "10", String.valueOf(2500 + 100 * i))));

        assertThat(outcomes).allMatch(o -> o.error == null);
        Map<String, Object> b = balance();
        assertThat((BigDecimal) b.get("q")).isEqualByComparingTo("100");
        assertThat((BigDecimal) b.get("v")).isEqualByComparingTo("295000.00"); // 10 x (2,500 + ... + 3,400)
        // one layer per receipt, and FIFO (layer id) order is the ledger's posting order
        List<Map<String, Object>> layers = jdbc.queryForList(
                "SELECT l.source_movement_id, l.remaining_quantity FROM stock_cost_layers l WHERE l.material_id = ? ORDER BY l.id", material);
        assertThat(layers).hasSize(10).allMatch(l -> ((BigDecimal) l.get("remaining_quantity")).compareTo(BigDecimal.TEN) == 0);
        assertThat(layers.stream().map(l -> (Long) l.get("source_movement_id")).toList()).isSorted();
        assertReconciles();
    }

    @Test
    void concurrentReturnsCannotPutBackMoreThanTheIssueTook() throws Exception {
        receive("20", "2000");
        receive("80", "2500");
        long issue = stock.issue(issue(UUID.randomUUID(), "30")).movement().id(); // 20 at 2,000 + 10 at 2,500

        // six returns of 10 against 30 issued: exactly three fit
        List<Outcome> outcomes = race(6, i -> () -> stock.returnStock(
                new StockReturnRequest(UUID.randomUUID(), issue, null, null, LocalDate.of(2026, 10, 21), new BigDecimal("10"), null, null)));

        assertThat(outcomes.stream().filter(o -> o.error == null)).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT sum(quantity_change) FROM stock_movements WHERE returned_from_movement_id = ?",
                BigDecimal.class, issue)).isEqualByComparingTo("30");
        // everything came back, at what it went out at
        assertThat(jdbc.queryForObject("SELECT sum(total_cost) FROM stock_movements WHERE returned_from_movement_id = ?",
                BigDecimal.class, issue)).isEqualByComparingTo("65000.00");
        assertThat(jdbc.queryForList("SELECT remaining_quantity FROM stock_cost_layers WHERE material_id = ? ORDER BY id",
                BigDecimal.class, material)).usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("20"), new BigDecimal("80"));
        assertThat((BigDecimal) balance().get("q")).isEqualByComparingTo("100");
        assertReconciles();
    }

    @Test
    void theSameSubmissionSentManyTimesAtOnceIsPostedOnce() throws Exception {
        UUID requestId = UUID.randomUUID();
        List<Outcome> outcomes = race(6, i -> () -> stock.receive(receipt(requestId, "10", "2500")));

        assertThat(outcomes).allMatch(o -> o.error == null);
        assertThat(outcomes.stream().filter(o -> o.created)).hasSize(1);
        assertThat((BigDecimal) balance().get("q")).isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_cost_layers WHERE material_id = ?", Long.class, material)).isOne();
    }

    @Test
    void aRetriedIssueRacingItselfDrawsTheLayersOnce() throws Exception {
        receive("10", "2000");
        receive("10", "2400");
        UUID requestId = UUID.randomUUID();

        List<Outcome> outcomes = race(6, i -> () -> stock.issue(issue(requestId, "12")));

        assertThat(outcomes).allMatch(o -> o.error == null);
        assertThat(outcomes.stream().filter(o -> o.created)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_cost_allocations WHERE material_id = ?", Long.class, material)).isEqualTo(2);
        assertThat((BigDecimal) balance().get("v")).isEqualByComparingTo("19200.00");
        assertReconciles();
    }

    @Test
    void aFailedIssueLeavesNoMovementNoAllocationAndNoChangedLayer() {
        receive("30", "2000");
        receive("70", "2500");
        Map<String, Object> before = balance();
        List<Map<String, Object>> layersBefore = layers();
        // the activity entry is the issue transaction's last write
        ActivityService spy = AopTestUtils.getUltimateTargetObject(activityService);
        doThrow(new IllegalStateException("activity log down")).when(spy).record(any(), any(), any());

        assertThatThrownBy(() -> stock.issue(issue(UUID.randomUUID(), "40"))).isInstanceOf(IllegalStateException.class);

        assertThat(balance()).isEqualTo(before);
        assertThat(layers()).isEqualTo(layersBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movements WHERE material_id = ? AND movement_type = 'ISSUED'",
                Long.class, material)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_cost_allocations WHERE material_id = ?", Long.class, material)).isZero();
    }

    private void receive(String quantity, String unitCost) {
        stock.receive(receipt(UUID.randomUUID(), quantity, unitCost));
    }

    private StockReceiptRequest receipt(UUID requestId, String quantity, String unitCost) {
        return new StockReceiptRequest(requestId, material, LocalDate.of(2026, 10, 20), new BigDecimal(quantity),
                new BigDecimal(unitCost), null, null, null);
    }

    private StockIssueRequest issue(UUID requestId, String quantity) {
        return new StockIssueRequest(requestId, material, project, LocalDate.of(2026, 10, 21), new BigDecimal(quantity), null, null);
    }

    private Map<String, Object> balance() {
        return jdbc.queryForMap("SELECT quantity_after AS q, value_after AS v FROM stock_movements WHERE material_id = ? "
                + "ORDER BY id DESC LIMIT 1", material);
    }

    private List<Map<String, Object>> layers() {
        return jdbc.queryForList("SELECT id, remaining_quantity, remaining_value, version FROM stock_cost_layers "
                + "WHERE material_id = ? ORDER BY id", material);
    }

    // Whatever order the racing transactions committed in: every running balance equals the plain sum up
    // to it, the open layers hold exactly the final balance, and every draw or return is fully explained
    // by its allocations.
    private void assertReconciles() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT quantity_change, total_cost, quantity_after, value_after "
                + "FROM stock_movements WHERE material_id = ? ORDER BY id", material);
        BigDecimal q = BigDecimal.ZERO;
        BigDecimal v = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            q = q.add((BigDecimal) row.get("quantity_change"));
            v = v.add((BigDecimal) row.get("total_cost"));
            assertThat((BigDecimal) row.get("quantity_after")).isEqualByComparingTo(q);
            assertThat((BigDecimal) row.get("value_after")).isEqualByComparingTo(v);
        }
        Map<String, Object> layers = jdbc.queryForMap("SELECT coalesce(sum(remaining_quantity), 0) AS q, "
                + "coalesce(sum(remaining_value), 0) AS v FROM stock_cost_layers WHERE material_id = ?", material);
        assertThat((BigDecimal) layers.get("q")).isEqualByComparingTo(q);
        assertThat((BigDecimal) layers.get("v")).isEqualByComparingTo(v);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM stock_movements m
                  LEFT JOIN (SELECT movement_id, sum(quantity) AS q, sum(cost) AS c FROM stock_cost_allocations GROUP BY movement_id) a
                    ON a.movement_id = m.id
                 WHERE m.material_id = ? AND NOT EXISTS (SELECT 1 FROM stock_cost_layers l WHERE l.source_movement_id = m.id)
                   AND (a.q IS DISTINCT FROM m.quantity_change OR a.c IS DISTINCT FROM m.total_cost)""",
                Long.class, material)).isZero();
    }

    // starts every task at the same moment and records how each ended
    private static List<Outcome> race(int count, IntFunction<Callable<MovementResult>> task) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Callable<MovementResult> work = task.apply(i);
                futures.add(pool.submit(() -> {
                    go.await();
                    try {
                        return new Outcome(null, work.call().created());
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
