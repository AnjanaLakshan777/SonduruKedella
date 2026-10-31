package com.sondurukedella.integration.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sondurukedella.config.ClockConfig;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// Billing, receipts, write-offs, deductions and receivables against the real schema, with a fixed
// clock. Each test rolls back. Concurrency and real-commit rollback are in RevenueConcurrencyIntegrationTest.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMIN")
class RevenueApiIntegrationTest {

    // 19:00 UTC on the 24th is 00:30 on the 25th in Colombo
    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-24T19:00:00Z"), ClockConfig.BUSINESS_ZONE);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private long project;
    private long advance;   // 300,000 due 2026-09-01 - overdue once billed
    private long roof;      // 400,000 due 2026-10-24 - overdue in Colombo (25th), not in UTC (24th)
    private long handover;  // 300,000 due 2026-12-20 - not due yet
    private long bankTransfer;

    @BeforeEach
    void setUp() throws Exception {
        long client = jdbc.queryForObject("INSERT INTO clients (name, phone) VALUES ('Revenue Client', '0710000000') RETURNING id", Long.class);
        project = createProject("""
                {"name":"Revenue House","type":"CONSTRUCTION","clientId":%d,"contractValue":1000000,
                 "startDate":"2026-03-01","expectedCompletion":"2026-09-30",
                 "schedule":[{"description":"Advance","dueDate":"2026-09-01","amount":300000},
                             {"description":"Roof","dueDate":"2026-10-24","amount":400000},
                             {"description":"Handover","dueDate":"2026-12-20","amount":300000}],
                 "delayTerms":{"contractDeadline":"2026-09-30","rule":"PER_DAY","ratePerDay":5000,
                               "graceDays":3,"maxDeduction":100000}}""".formatted(client));
        advance = installmentId("Advance");
        roof = installmentId("Roof");
        handover = installmentId("Handover");
        bankTransfer = jdbc.queryForObject("SELECT id FROM payment_methods WHERE name = 'Bank Transfer'", Long.class);
    }

    @Test
    void nothingIsReceivableUntilBilled() throws Exception {
        mockMvc.perform(get(base() + "/receivables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installments[*].status").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("UNBILLED"))))
                .andExpect(jsonPath("$.totals.outstanding").value(0))
                .andExpect(jsonPath("$.totals.revenueRecognised").value(0))
                .andExpect(jsonPath("$.totals.unbilled").value(1000000.00));
    }

    @Test
    void billingIsExplicitHappensOnceAndUsesTheColomboDate() throws Exception {
        bill(advance, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.billed").value(true))
                .andExpect(jsonPath("$.billedOn").value("2026-10-25"))
                // billed today and unpaid: owed straight away, so already overdue (0 days), whatever the planned date was
                .andExpect(jsonPath("$.status").value("OVERDUE"))
                .andExpect(jsonPath("$.daysOverdue").value(0))
                .andExpect(jsonPath("$.outstanding").value(300000.00));
        bill(advance, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("already billed")));

        mockMvc.perform(post(base() + "/installments/" + roof + "/bill").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":7}")).andExpect(status().isConflict());
        // billed on the 20th and "today" is the Colombo date, the 25th: 5 days late, counted from billing
        mockMvc.perform(post(base() + "/installments/" + roof + "/bill").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billedOn\":\"2026-10-20\",\"version\":0}"))
                .andExpect(jsonPath("$.billedOn").value("2026-10-20"))
                .andExpect(jsonPath("$.status").value("OVERDUE"))
                .andExpect(jsonPath("$.daysOverdue").value(5));

        assertThat(contractValue()).isEqualByComparingTo("1000000.00");
        assertThat(activity("NOTE")).containsExactly("Installment billed: Advance (LKR 300,000.00).",
                "Installment billed: Roof (LKR 400,000.00).");
    }

    @Test
    void receiptsArePartialOrFullButNeverMoreThanIsOwed() throws Exception {
        receipt(handover, UUID.randomUUID(), "100000").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Bill this installment")));
        bill(handover, null);

        UUID first = UUID.randomUUID();
        receipt(handover, first, "100000").andExpect(status().isCreated())
                .andExpect(jsonPath("$.method").value("Bank Transfer"))
                .andExpect(jsonPath("$.voided").value(false));
        expectInstallment(handover, "OVERDUE", "200000.00");

        receipt(handover, UUID.randomUUID(), "200000.01").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("More than the LKR 200,000.00 still owed on Handover."));
        receipt(handover, UUID.randomUUID(), "200000").andExpect(status().isCreated());
        expectInstallment(handover, "PAID", "0.00");
        receipt(handover, UUID.randomUUID(), "0.01").andExpect(status().isBadRequest());

        assertThat(activity("PAYMENT")).containsExactly(
                "Payment received: LKR 100,000.00 for Handover (Bank Transfer, TT-1).",
                "Payment received: LKR 200,000.00 for Handover (Bank Transfer, TT-1).");
    }

    @Test
    void aResentReceiptIsNotRecordedTwice() throws Exception {
        bill(handover, null);
        UUID requestId = UUID.randomUUID();

        long id = id(receipt(handover, requestId, "50000").andExpect(status().isCreated()));
        receipt(handover, requestId, "50000").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        // same id, different payment: a client bug, not a retry
        receipt(handover, requestId, "60000").andExpect(status().isConflict());
        receipt(roof, requestId, "50000").andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM client_payments WHERE request_id = ?", Long.class, requestId))
                .isEqualTo(1L);
        expectInstallment(handover, "OVERDUE", "250000.00");
    }

    @Test
    void aRemovedPaymentMethodCannotBeUsedForNewReceipts() throws Exception {
        bill(handover, null);
        long cheque = jdbc.queryForObject("SELECT id FROM payment_methods WHERE name = 'Cheque'", Long.class);
        jdbc.update("UPDATE payment_methods SET active = false WHERE id = ?", cheque);
        entityManager.clear(); // the whole test shares one persistence context

        mockMvc.perform(post(base() + "/installments/" + handover + "/receipts").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(receiptBody(UUID.randomUUID(), "1000", cheque)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("payment method")));
    }

    @Test
    void voidingKeepsTheReceiptButStopsItCounting() throws Exception {
        bill(handover, null);
        long id = id(receipt(handover, UUID.randomUUID(), "120000"));
        receipt(handover, UUID.randomUUID(), "30000");

        mockMvc.perform(post(base() + "/receipts/" + id + "/void").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Cheque bounced\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voided").value(true))
                .andExpect(jsonPath("$.voidReason").value("Cheque bounced"));
        mockMvc.perform(post(base() + "/receipts/" + id + "/void").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"again\",\"version\":1}")).andExpect(status().isConflict());

        expectInstallment(handover, "OVERDUE", "270000.00");
        mockMvc.perform(get(base() + "/receipts"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.voided == true)].id").value(org.hamcrest.Matchers.contains((int) id)));
        mockMvc.perform(get(base() + "/receipts/" + id)).andExpect(jsonPath("$.amount").value(120000.00));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM client_payments WHERE id = ?", Long.class, id)).isEqualTo(1L);
    }

    @Test
    void writeOffsReduceWhatIsOwedButAreNotReceipts() throws Exception {
        writeOff(handover, "50000", "Client cannot be traced since moving abroad.").andExpect(status().isBadRequest());
        bill(handover, null);
        writeOff(handover, "50000", "too short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("at least a sentence")));
        writeOff(handover, "300000.01", "Client cannot be traced since moving abroad.").andExpect(status().isBadRequest());

        writeOff(handover, "50000", "  Client cannot be traced since moving abroad.  ").andExpect(status().isCreated())
                .andExpect(jsonPath("$.reason").value("Client cannot be traced since moving abroad."));
        receipt(handover, UUID.randomUUID(), "250000.01").andExpect(status().isBadRequest());

        mockMvc.perform(get(base() + "/installments/" + handover))
                .andExpect(jsonPath("$.installment.writtenOff").value(50000.00))
                .andExpect(jsonPath("$.installment.received").value(0))
                .andExpect(jsonPath("$.installment.outstanding").value(250000.00))
                .andExpect(jsonPath("$.installment.status").value("OVERDUE"))
                .andExpect(jsonPath("$.writeOffs.length()").value(1));
        assertThat(activity("WRITEOFF")).containsExactly(
                "Bad debt written off: Client cannot be traced since moving abroad. (LKR 50,000.00, Handover)");
    }

    @Test
    void theDelayCalculationFollowsTheAgreedTerms() throws Exception {
        // 13 days late, 3 grace days: 10 x 5,000
        calculation("2026-10-13").andExpect(jsonPath("$.daysLate").value(13))
                .andExpect(jsonPath("$.chargeableDays").value(10))
                .andExpect(jsonPath("$.finalAmount").value(50000.00))
                .andExpect(jsonPath("$.capped").value(false));
        // 93 days late - 3 grace = 90 x 5,000 = 450,000, capped at 100,000
        calculation("2027-01-01").andExpect(jsonPath("$.calculatedAmount").value(450000.00))
                .andExpect(jsonPath("$.capped").value(true))
                .andExpect(jsonPath("$.finalAmount").value(100000.00));
        // no completion date anywhere: never guessed
        mockMvc.perform(get(base() + "/delay-terms/calculation")).andExpect(status().isBadRequest());
    }

    @Test
    void theProjectsCompletionDateIsTheDefault() throws Exception {
        mockMvc.perform(put(base() + "/status").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"COMPLETED\",\"version\":0}")).andExpect(status().isOk());
        // completed "today" (2026-10-25): 25 days late - 3 grace = 22 x 5,000 = 110,000, capped at 100,000
        mockMvc.perform(get(base() + "/delay-terms/calculation"))
                .andExpect(jsonPath("$.completionDate").value("2026-10-25"))
                .andExpect(jsonPath("$.finalAmount").value(100000.00));
    }

    @Test
    void aDeductionIsAppliedOnceWithinTheTermsAndCanBeReversed() throws Exception {
        apply(handover, null, "2026-10-13", 0).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("billed installment")));
        bill(handover, null);
        apply(handover, "60000", "2026-10-13", 0).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("agreed terms allow")));
        apply(handover, null, "2026-10-02", 0).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("grace days")));

        apply(handover, "40000", "2026-10-13", 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedAmount").value(40000.00))
                .andExpect(jsonPath("$.appliedToInstallmentId").value(handover))
                .andExpect(jsonPath("$.actualCompletion").value("2026-10-13"))
                .andExpect(jsonPath("$.appliedOn").value("2026-10-25"));
        expectInstallment(handover, "OVERDUE", "260000.00");
        mockMvc.perform(get(base() + "/receivables"))
                .andExpect(jsonPath("$.totals.deductions").value(40000.00))
                .andExpect(jsonPath("$.totals.revenueRecognised").value(260000.00));
        apply(handover, null, "2026-10-13", 1).andExpect(status().isConflict());

        reverse(0).andExpect(status().isConflict()); // stale
        reverse(1).andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedAmount").doesNotExist())
                .andExpect(jsonPath("$.actualCompletion").doesNotExist());
        reverse(2).andExpect(status().isConflict()); // nothing applied any more
        expectInstallment(handover, "OVERDUE", "300000.00");

        assertThat(contractValue()).isEqualByComparingTo("1000000.00");
        // the project's own completion date is never written by a deduction
        assertThat(jdbc.queryForObject("SELECT actual_completion FROM projects WHERE id = ?", java.sql.Date.class, project)).isNull();
        assertThat(activity("DEDUCTION")).containsExactly(
                "Delay deduction of LKR 40,000.00 applied to Handover (10 chargeable days). Agreed at handover.",
                "Delay deduction of LKR 40,000.00 reversed for review. Client disputed the dates.");
    }

    @Test
    void aDeductionCannotTakeMoreThanIsStillPayable() throws Exception {
        bill(handover, null);
        receipt(handover, UUID.randomUUID(), "270000");

        apply(handover, "50000", "2026-10-13", 0).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("More than the LKR 30,000.00 still payable on Handover."));
        apply(handover, "30000", "2026-10-13", 0).andExpect(status().isOk());
        expectInstallment(handover, "PAID", "0.00");
    }

    @Test
    void percentAndFixedRulesUseTheProjectsOwnContractValue() throws Exception {
        long client = jdbc.queryForObject("INSERT INTO clients (name, phone) VALUES ('Rules Client', '0710000000') RETURNING id", Long.class);
        long pct = createProject(construction(client, "2000000", "\"rule\":\"PERCENT_PER_WEEK\",\"percentPerWeek\":0.5"));
        long fixed = createProject(construction(client, "2000000", "\"rule\":\"FIXED_AMOUNT\",\"fixedAmount\":75000"));

        // 15 days late, no grace: 2 full weeks x 0.5% x 2,000,000
        mockMvc.perform(get("/api/v1/projects/" + pct + "/delay-terms/calculation").param("completionDate", "2026-10-15"))
                .andExpect(jsonPath("$.finalAmount").value(20000.00));
        mockMvc.perform(get("/api/v1/projects/" + fixed + "/delay-terms/calculation").param("completionDate", "2026-10-01"))
                .andExpect(jsonPath("$.finalAmount").value(75000.00));
    }

    @Test
    void projectsWithoutTermsHaveNoDeductionToApply() throws Exception {
        long client = jdbc.queryForObject("INSERT INTO clients (name, phone) VALUES ('Design Client', '0710000000') RETURNING id", Long.class);
        long design = createProject("""
                {"name":"Plan","type":"DESIGN","clientId":%d,"contractValue":100000,"startDate":"2026-01-01",
                 "expectedCompletion":"2026-03-01","schedule":[{"description":"Fee","dueDate":"2026-01-01","amount":100000}]}"""
                .formatted(client));

        mockMvc.perform(get("/api/v1/projects/" + design + "/delay-terms/calculation").param("completionDate", "2026-05-01"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("This project has no delay deduction terms."));
        mockMvc.perform(get("/api/v1/projects/" + design + "/receivables")).andExpect(status().isOk());
    }

    @Test
    void totalsAndTheSummaryAddUp() throws Exception {
        bill(advance, null);   // 300,000, paid in full below
        bill(roof, "2026-10-20"); // 400,000, billed 5 days ago so overdue
        bill(handover, null);  // 300,000, billed today and unpaid, so overdue too
        receipt(advance, UUID.randomUUID(), "300000");                 // paid
        receipt(roof, UUID.randomUUID(), "150000");                    // overdue, partly paid
        writeOff(roof, "50000", "Agreed discount for snagging works on the roof.");
        apply(handover, "20000", "2026-10-13", 0);                      // deduction on the open one

        mockMvc.perform(get(base() + "/receivables"))
                .andExpect(jsonPath("$.installments[0].status").value("PAID"))
                .andExpect(jsonPath("$.installments[1].status").value("OVERDUE"))
                .andExpect(jsonPath("$.installments[2].status").value("OVERDUE"))
                .andExpect(jsonPath("$.totals.billed").value(1000000.00))
                .andExpect(jsonPath("$.totals.deductions").value(20000.00))
                .andExpect(jsonPath("$.totals.revenueRecognised").value(980000.00))
                .andExpect(jsonPath("$.totals.received").value(450000.00))
                .andExpect(jsonPath("$.totals.writtenOff").value(50000.00))
                .andExpect(jsonPath("$.totals.outstanding").value(480000.00))  // 200,000 roof + 280,000 handover
                .andExpect(jsonPath("$.totals.overdue").value(480000.00))  // all of it: billed and unpaid is overdue
                .andExpect(jsonPath("$.totals.unbilled").value(0));

        mockMvc.perform(get("/api/v1/receivables/summary"))
                .andExpect(jsonPath("$.outstanding").value(480000.00))
                .andExpect(jsonPath("$.overdue").value(480000.00))
                .andExpect(jsonPath("$.partiallyPaid").value(200000.00))
                .andExpect(jsonPath("$.writtenOff").value(50000.00))
                .andExpect(jsonPath("$.openInstallments").value(2))
                .andExpect(jsonPath("$.overdueInstallments").value(2))
                .andExpect(jsonPath("$.partiallyPaidInstallments").value(1));
    }

    @Test
    void readingChangesNothing() throws Exception {
        bill(handover, null);
        receipt(handover, UUID.randomUUID(), "10000");
        entityManager.flush();
        List<Map<String, Object>> before = snapshot();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get(base() + "/receivables")).andExpect(status().isOk());
            mockMvc.perform(get(base() + "/installments/" + handover)).andExpect(status().isOk());
            mockMvc.perform(get(base() + "/receipts")).andExpect(status().isOk());
            mockMvc.perform(get(base() + "/delay-terms/calculation").param("completionDate", "2026-10-13")).andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/receivables/summary")).andExpect(status().isOk());
        }
        entityManager.flush();
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void unknownProjectsInstallmentsAndReceiptsAre404() throws Exception {
        String missing = "/api/v1/projects/" + Long.MAX_VALUE;
        mockMvc.perform(get(missing + "/receivables")).andExpect(status().isNotFound());
        mockMvc.perform(get(missing + "/receipts")).andExpect(status().isNotFound());
        mockMvc.perform(get(missing + "/delay-terms/calculation").param("completionDate", "2026-10-13")).andExpect(status().isNotFound());
        mockMvc.perform(get(base() + "/installments/" + Long.MAX_VALUE)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Installment not found."));
        mockMvc.perform(get(base() + "/receipts/" + Long.MAX_VALUE)).andExpect(status().isNotFound());
        bill(Long.MAX_VALUE, null).andExpect(status().isNotFound());

        // an installment of this project, addressed through another project, is not found either
        long client = jdbc.queryForObject("INSERT INTO clients (name, phone) VALUES ('Other Client', '0710000000') RETURNING id", Long.class);
        long other = createProject(construction(client, "500000", "\"rule\":\"FIXED_AMOUNT\",\"fixedAmount\":1000"));
        mockMvc.perform(post("/api/v1/projects/" + other + "/installments/" + handover + "/bill").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT billed_on FROM installments WHERE id = ?", java.sql.Date.class, handover)).isNull();
    }

    private ResultActions bill(long installmentId, String date) throws Exception {
        long version = installmentId == Long.MAX_VALUE ? 0
                : jdbc.queryForObject("SELECT version FROM installments WHERE id = ?", Long.class, installmentId);
        String body = date == null ? "{\"version\":%d}".formatted(version) : "{\"billedOn\":\"%s\",\"version\":%d}".formatted(date, version);
        return mockMvc.perform(post(base() + "/installments/" + installmentId + "/bill").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions receipt(long installmentId, UUID requestId, String amount) throws Exception {
        return mockMvc.perform(post(base() + "/installments/" + installmentId + "/receipts").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(receiptBody(requestId, amount, bankTransfer)));
    }

    private static String receiptBody(UUID requestId, String amount, long method) {
        return """
                {"requestId":"%s","paymentDate":"2026-10-20","amount":%s,"paymentMethodId":%d,"reference":"TT-1"}"""
                .formatted(requestId, amount, method);
    }

    private ResultActions writeOff(long installmentId, String amount, String reason) throws Exception {
        return mockMvc.perform(post(base() + "/installments/" + installmentId + "/write-offs").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-10-22\",\"amount\":%s,\"reason\":\"%s\"}".formatted(amount, reason)));
    }

    private ResultActions apply(long installmentId, String amount, String completion, long version) throws Exception {
        String amountField = amount == null ? "" : "\"amount\":" + amount + ",";
        return mockMvc.perform(post(base() + "/delay-terms/deduction").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"installmentId\":%d,%s\"completionDate\":\"%s\",\"notes\":\"Agreed at handover.\",\"version\":%d}"
                        .formatted(installmentId, amountField, completion, version)));
    }

    private ResultActions reverse(long version) throws Exception {
        return mockMvc.perform(post(base() + "/delay-terms/deduction/reverse").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Client disputed the dates.\",\"version\":%d}".formatted(version)));
    }

    private ResultActions calculation(String completion) throws Exception {
        return mockMvc.perform(get(base() + "/delay-terms/calculation").param("completionDate", completion))
                .andExpect(status().isOk());
    }

    private void expectInstallment(long installmentId, String status, String outstanding) throws Exception {
        mockMvc.perform(get(base() + "/installments/" + installmentId))
                .andExpect(jsonPath("$.installment.status").value(status))
                .andExpect(jsonPath("$.installment.outstanding").value(new BigDecimal(outstanding).doubleValue()));
    }

    private static String construction(long clientId, String contract, String rule) {
        return """
                {"name":"Rule House","type":"CONSTRUCTION","clientId":%d,"contractValue":%s,
                 "startDate":"2026-03-01","expectedCompletion":"2026-09-30",
                 "schedule":[{"description":"All","dueDate":"2026-09-30","amount":%s}],
                 "delayTerms":{"contractDeadline":"2026-09-30",%s}}""".formatted(clientId, contract, contract, rule);
    }

    private long createProject(String body) throws Exception {
        String json = mockMvc.perform(post("/api/v1/projects").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private long installmentId(String description) {
        return jdbc.queryForObject("SELECT id FROM installments WHERE project_id = ? AND description = ?", Long.class,
                project, description);
    }

    private BigDecimal contractValue() {
        return jdbc.queryForObject("SELECT contract_value FROM projects WHERE id = ?", BigDecimal.class, project);
    }

    private List<String> activity(String kind) {
        return jdbc.queryForList("SELECT message FROM activity_log WHERE project_id = ? AND kind = ? ORDER BY id",
                String.class, project, kind);
    }

    private List<Map<String, Object>> snapshot() {
        return List.of(
                Map.of("installments", jdbc.queryForList("SELECT * FROM installments WHERE project_id = ? ORDER BY id", project)),
                Map.of("payments", jdbc.queryForList("SELECT * FROM client_payments ORDER BY id")),
                Map.of("writeOffs", jdbc.queryForList("SELECT * FROM receivable_write_offs ORDER BY id")),
                Map.of("terms", jdbc.queryForList("SELECT * FROM delay_deduction_terms WHERE project_id = ?", project)),
                Map.of("activity", jdbc.queryForObject("SELECT count(*) FROM activity_log", Long.class)));
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String base() {
        return "/api/v1/projects/" + project;
    }
}
