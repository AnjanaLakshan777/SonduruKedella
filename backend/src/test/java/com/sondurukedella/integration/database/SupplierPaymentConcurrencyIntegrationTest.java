package com.sondurukedella.integration.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.sondurukedella.suppliers.dto.SupplierPaymentRequest;
import com.sondurukedella.suppliers.service.SupplierInvoiceService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

// Not @Transactional: concurrent payments on one invoice must never add up to more than its total.
// Everything belongs to the supplier "SPTEST Supplier" and is deleted afterwards. Throwaway DB only.
@SpringBootTest
class SupplierPaymentConcurrencyIntegrationTest {

    @Autowired
    private SupplierInvoiceService service;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        String ours = "SELECT i.id FROM supplier_invoices i JOIN suppliers s ON s.id = i.supplier_id WHERE s.name = 'SPTEST Supplier'";
        // supplier_payments refuses deletes (V1 trigger); lift it for this cleanup only
        jdbc.execute("ALTER TABLE supplier_payments DISABLE TRIGGER trg_supplier_payments_guard");
        try {
            jdbc.update("DELETE FROM supplier_payments WHERE invoice_id IN (" + ours + ")");
        } finally {
            jdbc.execute("ALTER TABLE supplier_payments ENABLE TRIGGER trg_supplier_payments_guard");
        }
        jdbc.update("DELETE FROM activity_log WHERE message LIKE '%SPTEST-INV%'");
        jdbc.update("DELETE FROM supplier_invoices WHERE id IN (" + ours + ")");
        jdbc.update("DELETE FROM suppliers WHERE name = 'SPTEST Supplier'");
    }

    @Test
    void concurrentPaymentsNeverExceedTheInvoice() throws Exception {
        long supplier = jdbc.queryForObject("INSERT INTO suppliers (name, phone) VALUES ('SPTEST Supplier', '0710000000') RETURNING id", Long.class);
        long invoice = jdbc.queryForObject("""
                INSERT INTO supplier_invoices (supplier_id, invoice_no, invoice_date, due_date, allocation, total_amount)
                VALUES (?, 'SPTEST-INV', DATE '2026-10-01', DATE '2026-10-31', 'STOCK', 100000) RETURNING id""", Long.class, supplier);
        long cash = jdbc.queryForObject("SELECT id FROM payment_methods WHERE name = 'Cash'", Long.class);

        // five payments of 40,000 at once: only two fit
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 5; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        service.recordPayment(invoice, new SupplierPaymentRequest(UUID.randomUUID(), LocalDate.of(2026, 10, 5),
                                new BigDecimal("40000"), cash, null, null));
                        return true;
                    } catch (RuntimeException ex) {
                        return false;
                    }
                }));
            }
            start.countDown();
            long succeeded = 0;
            for (Future<Boolean> f : results) {
                if (f.get(30, TimeUnit.SECONDS)) {
                    succeeded++;
                }
            }
            assertThat(succeeded).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT sum(amount)::text FROM supplier_payments WHERE invoice_id = ?", String.class, invoice))
                .isEqualTo("80000.00");
    }
}
