package com.sondurukedella.integration.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sondurukedella.config.ClockConfig;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// Commissions end to end on the real schema - drafts, confirming, the 80/20 design split,
// construction, payments, voids and the company-wide views. Rolls back.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMIN")
class CommissionApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private long cash;
    private long design;
    private long construction;
    private final LocalDate today = LocalDate.now(ClockConfig.BUSINESS_ZONE);

    @BeforeEach
    void setUp() throws Exception {
        cash = jdbc.queryForObject("SELECT id FROM payment_methods WHERE name = 'Cash'", Long.class);
        // the seeded policy: 20% design pool, no construction level
        jdbc.update("UPDATE commission_policy SET design_commission_pool_pct = 20, construction_warning_pct = NULL WHERE id = 1");
        long client = jdbc.queryForObject("INSERT INTO clients (name, phone) VALUES ('Commission Client', '0710000000') RETURNING id", Long.class);
        design = project(client, "DESIGN", "1500000");
        construction = project(client, "CONSTRUCTION", "10000000");
    }

    @Test
    void aDraftIsSavedButIsNotACostYet() throws Exception {
        save(design, false, null, row(null, null, "  Nimal   Perera ", "3D designer", "12"), row(null, null, "Kamala", "", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrangements.length()").value(2))
                .andExpect(jsonPath("$.arrangements[0].recipient").value("Nimal Perera"))
                .andExpect(jsonPath("$.arrangements[1].role").value("Commission"))
                .andExpect(jsonPath("$.arrangements[0].status").value("PENDING"))
                .andExpect(jsonPath("$.arrangements[0].amount").value(180000.00))
                .andExpect(jsonPath("$.arrangements[0].outstanding").value(0))
                .andExpect(jsonPath("$.totals.committed").value(0))
                .andExpect(jsonPath("$.totals.pendingAmount").value(300000.00))
                .andExpect(jsonPath("$.policy.totalRatePct").value(20))
                .andExpect(jsonPath("$.policy.exceeded").value(false));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commission_arrangements WHERE project_id = ? AND commission_amount IS NULL",
                Long.class, design)).isEqualTo(2);
        assertThat(activity(design)).anyMatch(m -> m.contains("saved as draft"));
    }

    @Test
    void confirmingStoresEachAmountAndTheDesignSplitAddsUp() throws Exception {
        save(design, true, null, row(null, null, "Nimal Perera", "3D designer", "12"), row(null, null, "Kamala", null, "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrangements[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.arrangements[0].amount").value(180000.00))
                .andExpect(jsonPath("$.arrangements[1].amount").value(120000.00))
                .andExpect(jsonPath("$.arrangements[0].confirmedOn").value(today.toString()))
                .andExpect(jsonPath("$.arrangements[0].outstanding").value(180000.00))
                .andExpect(jsonPath("$.arrangements[0].policyException").value(false))
                .andExpect(jsonPath("$.designSplit.poolPct").value(20))
                .andExpect(jsonPath("$.designSplit.companySharePct").value(80))
                .andExpect(jsonPath("$.designSplit.poolAmount").value(300000.00))
                .andExpect(jsonPath("$.designSplit.companyShareAmount").value(1200000.00))
                .andExpect(jsonPath("$.designSplit.unallocatedAmount").value(0))
                .andExpect(jsonPath("$.totals.committed").value(300000.00))
                .andExpect(jsonPath("$.totals.outstanding").value(300000.00));
        assertThat(jdbc.queryForList("SELECT commission_amount::text FROM commission_arrangements WHERE project_id = ? ORDER BY id",
                String.class, design)).containsExactly("180000.00", "120000.00");
        assertThat(activity(design)).anyMatch(m -> m.contains("confirmed (Nimal Perera 12%, Kamala 8%)"));
    }

    @Test
    void theResidualCentGoesToTheCompanyShare() throws Exception {
        long client = jdbc.queryForObject("SELECT client_id FROM projects WHERE id = ?", Long.class, design);
        long odd = project(client, "DESIGN", "333333.33");
        save(odd, true, null, row(null, null, "Asanka", null, "12.5"), row(null, null, "Ruwan", null, "7.5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrangements[0].amount").value(41666.67))
                .andExpect(jsonPath("$.arrangements[1].amount").value(25000.00))
                .andExpect(jsonPath("$.designSplit.poolAmount").value(66666.67))
                .andExpect(jsonPath("$.designSplit.companyShareAmount").value(266666.66))
                .andExpect(jsonPath("$.designSplit.allocatedAmount").value(66666.67))
                .andExpect(jsonPath("$.designSplit.unallocatedAmount").value(0));
    }

    @Test
    void goingOverThePoolNeedsAnExplicitExceptionAndOtherwiseChangesNothing() throws Exception {
        save(design, true, null, row(null, null, "Nimal", null, "15"), row(null, null, "Kamala", null, "7"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("22%")))
                .andExpect(jsonPath("$.detail").value(containsString("Nothing was changed")));
        assertThat(count(design)).isZero();

        // a draft over the pool is allowed and carries the warning
        save(design, false, null, row(null, null, "Nimal", null, "15"), row(null, null, "Kamala", null, "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policy.exceeded").value(true))
                .andExpect(jsonPath("$.policy.warning").value(containsString("design commission pool")));

        String json = projectJson(design);
        save(design, true, true, row(id(json, 0), version(json, 0), "Nimal", null, "15"), row(id(json, 1), version(json, 1), "Kamala", null, "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrangements[0].policyException").value(true))
                .andExpect(jsonPath("$.designSplit.unallocatedAmount").value(-30000.00));
        assertThat(activity(design)).anyMatch(m -> m.contains("policy exception"));
    }

    @Test
    void constructionUsesOnlyTheAgreedRatesAndItsOwnOptionalLevel() throws Exception {
        // no construction level configured: no warning at all, and the design pool doesn't apply
        save(construction, true, null, row(null, null, "Site Engineer", "Referral", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designSplit").doesNotExist())
                .andExpect(jsonPath("$.policy.limitPct").doesNotExist())
                .andExpect(jsonPath("$.arrangements[0].amount").value(2500000.00));

        setPolicy("20", "3");
        mockMvc.perform(get("/api/v1/projects/" + construction + "/commissions"))
                .andExpect(jsonPath("$.policy.limitPct").value(3))
                .andExpect(jsonPath("$.policy.exceeded").value(true))
                .andExpect(jsonPath("$.policy.warning").value(containsString("construction")));
    }

    @Test
    void consultationProjectsHaveNoCommission() throws Exception {
        long client = jdbc.queryForObject("SELECT client_id FROM projects WHERE id = ?", Long.class, design);
        long consultation = project(client, "CONSULTATION", "50000");
        save(consultation, false, null, row(null, null, "Nimal", null, "5")).andExpect(status().isUnprocessableContent());
        save(999_999_999L, false, null, row(null, null, "Nimal", null, "5")).andExpect(status().isNotFound());
    }

    @Test
    void invalidRowsAreRejected() throws Exception {
        save(design, false, null, row(null, null, " ", null, "5")).andExpect(status().isBadRequest());
        save(design, false, null, row(null, null, "Nimal", null, "0")).andExpect(status().isBadRequest());
        save(design, false, null, row(null, null, "Nimal", null, "100")).andExpect(status().isBadRequest());
        save(design, false, null, row(null, null, "Nimal", null, "1.00001")).andExpect(status().isBadRequest());
        save(design, false, null, row(null, null, "Nimal", "Designer", "5"), row(null, null, "nimal", "designer", "6"))
                .andExpect(status().isBadRequest());
        save(design, true, null).andExpect(status().isBadRequest());
        assertThat(count(design)).isZero();
    }

    @Test
    void resendingTheSameSaveCreatesNoDuplicates() throws Exception {
        for (int i = 0; i < 3; i++) {
            save(design, true, null, row(null, null, "Nimal", "Designer", "12")).andExpect(status().isOk());
        }
        assertThat(count(design)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM activity_log WHERE project_id = ? AND message LIKE '%confirmed%'",
                Long.class, design)).isOne();
    }

    @Test
    void draftsCanBeRevisedOrRemovedButConfirmedOnesAreFrozen() throws Exception {
        String json = body(save(design, false, null, row(null, null, "Nimal", null, "10"), row(null, null, "Kamala", null, "5")));
        // revise Nimal, drop Kamala
        json = body(save(design, false, null, row(id(json, 0), version(json, 0), "Nimal", "Lead", "11")));
        assertThat(count(design)).isOne();
        assertThat((String) JsonPath.read(json, "$.arrangements[0].role")).isEqualTo("Lead");

        json = body(save(design, true, null, row(id(json, 0), version(json, 0), "Nimal", "Lead", "11")));
        long id = id(json, 0);
        long v = version(json, 0);
        save(design, true, null, row(id, v, "Nimal", "Lead", "12"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("Void it")));
        save(design, false, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("can't be removed")));
        save(design, false, null, row(id, v + 7, "Nimal", "Lead", "11")).andExpect(status().isConflict());
        save(design, false, null, row(id, null, "Nimal", "Lead", "11")).andExpect(status().isBadRequest());

        // adding someone next to a confirmed row is fine
        save(design, false, null, row(id, v, "Nimal", "Lead", "11"), row(null, null, "Kamala", null, "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrangements[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.arrangements[1].status").value("PENDING"));
    }

    @Test
    void historyDoesNotMoveWhenTheSettingsChange() throws Exception {
        save(design, true, null, row(null, null, "Nimal", null, "12")).andExpect(status().isOk());
        setPolicy("10", null);
        mockMvc.perform(get("/api/v1/projects/" + design + "/commissions"))
                .andExpect(jsonPath("$.arrangements[0].amount").value(180000.00))
                .andExpect(jsonPath("$.arrangements[0].ratePct").value(12))
                .andExpect(jsonPath("$.arrangements[0].policyException").value(false))
                // the current policy now flags it, but the stored row is unchanged
                .andExpect(jsonPath("$.policy.exceeded").value(true))
                .andExpect(jsonPath("$.designSplit.poolAmount").value(150000.00));
        assertThat(jdbc.queryForObject("SELECT commission_amount::text FROM commission_arrangements WHERE project_id = ?", String.class, design))
                .isEqualTo("180000.00");
    }

    @Test
    void paymentsSettleTheCommissionAndNeverOverpay() throws Exception {
        long id = confirmOne(design, "Nimal", "12"); // 180,000
        pay(id, UUID.randomUUID(), "50000", today).andExpect(status().isCreated())
                .andExpect(jsonPath("$.method").value("Cash"))
                .andExpect(jsonPath("$.recipient").value("Nimal"));
        commission(id).andExpect(jsonPath("$.commission.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.commission.paid").value(50000.00))
                .andExpect(jsonPath("$.commission.outstanding").value(130000.00));

        pay(id, UUID.randomUUID(), "130000.01", today).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("130,000.00")));
        pay(id, UUID.randomUUID(), "-5", today).andExpect(status().isBadRequest());
        pay(id, UUID.randomUUID(), "10.001", today).andExpect(status().isBadRequest());
        pay(id, UUID.randomUUID(), "10", today.plusDays(1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("future")));

        pay(id, UUID.randomUUID(), "130000", today.minusDays(3)).andExpect(status().isCreated());
        commission(id).andExpect(jsonPath("$.commission.status").value("PAID"))
                .andExpect(jsonPath("$.commission.outstanding").value(0))
                .andExpect(jsonPath("$.payments.length()").value(2));
        pay(id, UUID.randomUUID(), "0.01", today).andExpect(status().isBadRequest());
        assertThat(activity(design)).anyMatch(m -> m.contains("Commission payment: LKR 50,000.00 to Nimal"));
    }

    @Test
    void aResentPaymentIsRecordedOnce() throws Exception {
        long id = confirmOne(design, "Nimal", "12");
        UUID request = UUID.randomUUID();
        long first = ((Number) JsonPath.read(body(pay(id, request, "1000", today).andExpect(status().isCreated())), "$.id")).longValue();
        long again = ((Number) JsonPath.read(body(pay(id, request, "1000", today).andExpect(status().isOk())), "$.id")).longValue();
        assertThat(again).isEqualTo(first);
        pay(id, request, "2000", today).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commission_payments WHERE arrangement_id = ?", Long.class, id)).isOne();
    }

    @Test
    void draftsAndVoidedCommissionsCannotBePaid() throws Exception {
        String json = body(save(design, false, null, row(null, null, "Nimal", null, "12")));
        pay(id(json, 0), UUID.randomUUID(), "100", today).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("Confirm")));
        pay(999_999_999L, UUID.randomUUID(), "100", today).andExpect(status().isNotFound());
    }

    @Test
    void voidingAPaymentRestoresTheBalanceAndKeepsTheRow() throws Exception {
        long id = confirmOne(design, "Nimal", "12");
        String payment = body(pay(id, UUID.randomUUID(), "180000", today));
        long paymentId = ((Number) JsonPath.read(payment, "$.id")).longValue();
        voidPayment(id, paymentId, "", 0).andExpect(status().isBadRequest());
        voidPayment(id, paymentId, "Paid to the wrong person", 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.voided").value(true))
                .andExpect(jsonPath("$.voidReason").value("Paid to the wrong person"));
        voidPayment(id, paymentId, "again", 1).andExpect(status().isConflict());
        commission(id).andExpect(jsonPath("$.commission.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.commission.outstanding").value(180000.00))
                .andExpect(jsonPath("$.payments[0].voided").value(true));
        // the full amount can be paid again
        pay(id, UUID.randomUUID(), "180000", today).andExpect(status().isCreated());
    }

    @Test
    void aConfirmedCommissionIsCorrectedByVoidingItAndAddingTheRightOne() throws Exception {
        long id = confirmOne(design, "Nimal", "12");
        long paymentId = ((Number) JsonPath.read(body(pay(id, UUID.randomUUID(), "1000", today)), "$.id")).longValue();
        long v = jdbc.queryForObject("SELECT version FROM commission_arrangements WHERE id = ?", Long.class, id);

        voidCommission(id, "Wrong rate", v).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("Void its payments first")));
        voidPayment(id, paymentId, "Part of the correction", 0).andExpect(status().isOk());
        voidCommission(id, " ", v).andExpect(status().isBadRequest());
        voidCommission(id, "Wrong rate", v + 3).andExpect(status().isConflict());
        voidCommission(id, "Wrong rate, agreed 10%", v).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOIDED"))
                .andExpect(jsonPath("$.amount").value(180000.00))
                .andExpect(jsonPath("$.outstanding").value(0))
                .andExpect(jsonPath("$.voidReason").value("Wrong rate, agreed 10%"));
        voidCommission(id, "again", v + 1).andExpect(status().isConflict());
        pay(id, UUID.randomUUID(), "100", today).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("voided")));

        // the same recipient and role can now be added again; the voided row stays in the history
        save(design, true, null, row(null, null, "Nimal", null, "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrangements.length()").value(2))
                .andExpect(jsonPath("$.arrangements[0].status").value("VOIDED"))
                .andExpect(jsonPath("$.arrangements[1].amount").value(150000.00))
                .andExpect(jsonPath("$.totals.committed").value(150000.00));
        assertThat(activity(design)).anyMatch(m -> m.contains("voided: Wrong rate, agreed 10%"));
    }

    @Test
    void aDraftCannotBeVoided() throws Exception {
        String json = body(save(design, false, null, row(null, null, "Nimal", null, "12")));
        voidCommission(id(json, 0), "nope", version(json, 0)).andExpect(status().isUnprocessableContent());
        voidCommission(999_999_999L, "nope", 0).andExpect(status().isNotFound());
    }

    @Test
    void theDirectorySummaryAndRecipientViewsAddUp() throws Exception {
        long a = confirmOne(design, "Nimal Perera", "12");        // 180,000
        long b = confirmOne(construction, "nimal perera", "1"); // 100,000, same person in lower case
        save(design, false, null, row(a, jdbc.queryForObject("SELECT version FROM commission_arrangements WHERE id = ?", Long.class, a),
                "Nimal Perera", null, "12"), row(null, null, "Draft Person", null, "2")).andExpect(status().isOk());
        pay(a, UUID.randomUUID(), "180000", today).andExpect(status().isCreated());
        pay(b, UUID.randomUUID(), "40000", today).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/commissions").param("recipient", "NIMAL PERERA").param("sort", "id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].status").value("PAID"))
                .andExpect(jsonPath("$.content[1].status").value("PARTIALLY_PAID"));
        mockMvc.perform(get("/api/v1/commissions").param("status", "PAID").param("projectId", String.valueOf(design)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/commissions").param("type", "CONSTRUCTION").param("projectId", String.valueOf(construction)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].projectType").value("CONSTRUCTION"));
        mockMvc.perform(get("/api/v1/commissions").param("status", "PENDING").param("projectId", String.valueOf(design)))
                .andExpect(jsonPath("$.content[0].recipient").value("Draft Person"));
        mockMvc.perform(get("/api/v1/commissions").param("from", today.toString()).param("to", today.toString())
                        .param("projectId", String.valueOf(design)))
                .andExpect(jsonPath("$.totalElements").value(1)); // the draft has no confirmed date
        mockMvc.perform(get("/api/v1/commissions").param("sort", "rate")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/commissions").param("size", "101")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/commissions").param("from", "2026-02-01").param("to", "2026-01-01")).andExpect(status().isBadRequest());

        // the whole database may hold other rows, so check this test's share through the recipient view
        String recipients = mockMvc.perform(get("/api/v1/commissions/recipients")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> committed = JsonPath.read(recipients, "$[?(@.recipient == 'nimal perera' || @.recipient == 'Nimal Perera')].committed");
        List<Number> outstanding = JsonPath.read(recipients, "$[?(@.recipient == 'nimal perera' || @.recipient == 'Nimal Perera')].outstanding");
        List<Number> arrangements = JsonPath.read(recipients, "$[?(@.recipient == 'nimal perera' || @.recipient == 'Nimal Perera')].arrangements");
        assertThat(committed).singleElement().satisfies(n -> assertThat(n.doubleValue()).isEqualTo(280000.0));
        assertThat(outstanding).singleElement().satisfies(n -> assertThat(n.doubleValue()).isEqualTo(60000.0));
        assertThat(arrangements).singleElement().satisfies(n -> assertThat(n.intValue()).isEqualTo(2));

        mockMvc.perform(get("/api/v1/commissions/summary").param("type", "DESIGN")).andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").isNumber());
        mockMvc.perform(get("/api/v1/commissions/payments").param("projectId", String.valueOf(construction)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(40000.00));
        mockMvc.perform(get("/api/v1/commissions/" + a + "/payments")).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void theSummaryCountsConfirmedCostsOnly() throws Exception {
        String before = mockMvc.perform(get("/api/v1/commissions/summary")).andReturn().getResponse().getContentAsString();
        long id = confirmOne(design, "Nimal", "12");
        save(construction, false, null, row(null, null, "Draft", null, "2")).andExpect(status().isOk());
        pay(id, UUID.randomUUID(), "30000", today).andExpect(status().isCreated());
        String after = mockMvc.perform(get("/api/v1/commissions/summary")).andReturn().getResponse().getContentAsString();
        assertThat(delta(before, after, "$.committed")).isEqualTo(180000.0);
        assertThat(delta(before, after, "$.paid")).isEqualTo(30000.0);
        assertThat(delta(before, after, "$.outstanding")).isEqualTo(150000.0);
        assertThat(delta(before, after, "$.confirmedCount")).isEqualTo(1.0);
        assertThat(delta(before, after, "$.pendingCount")).isEqualTo(1.0);
        assertThat(delta(before, after, "$.paymentCount")).isEqualTo(1.0);
    }

    @Test
    void commissionIsNeverCountedAgainAsAnExpense() throws Exception {
        long expensesBefore = jdbc.queryForObject("SELECT count(*) FROM expenses", Long.class);
        long id = confirmOne(construction, "Nimal", "1");
        pay(id, UUID.randomUUID(), "100000", today).andExpect(status().isCreated());
        // neither confirming nor paying writes an expense; the cost lives only in the commission ledger
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses", Long.class)).isEqualTo(expensesBefore);
        // the Commission category (seeded for construction; design has none) can't be used for a manual
        // expense, even directly in SQL
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expense_categories WHERE name = 'Commission' AND expense_group <> 'CONSTRUCTION'",
                Long.class)).isZero();
        long commissionCategory = jdbc.queryForObject(
                "SELECT id FROM expense_categories WHERE expense_group = 'CONSTRUCTION' AND name = 'Commission'", Long.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO expenses (expense_date, scope, project_id, project_type, category_id, expense_group,
                                      description, payee, amount)
                VALUES (CURRENT_DATE, 'PROJECT', ?, 'CONSTRUCTION', ?, 'CONSTRUCTION', 'Commission', 'Nimal', 100000)""",
                construction, commissionCategory)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private long project(long client, String type, String contractValue) throws Exception {
        String json = mockMvc.perform(post("/api/v1/projects").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Commission %s","type":"%s","clientId":%d,"contractValue":%s,"startDate":"2026-01-01",
                         "expectedCompletion":"2026-12-31","schedule":[{"description":"All","dueDate":"2026-12-31","amount":%s}]}"""
                        .formatted(type, type, client, contractValue, contractValue)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    // go through the Settings API (Super Admin) so the service sees the new policy in this same transaction
    private void setPolicy(String pool, String construction) throws Exception {
        String current = mockMvc.perform(get("/api/v1/settings/commission-policy")).andReturn().getResponse().getContentAsString();
        long version = ((Number) JsonPath.read(current, "$.version")).longValue();
        mockMvc.perform(put("/api/v1/settings/commission-policy").with(user("root").roles("SUPER_ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designCommissionPool\":" + pool + (construction == null ? "" : ",\"constructionDefaultRate\":" + construction)
                                + ",\"version\":" + version + "}"))
                .andExpect(status().isOk());
    }

    private long confirmOne(long projectId, String recipient, String rate) throws Exception {
        String json = body(save(projectId, true, null, row(null, null, recipient, null, rate)).andExpect(status().isOk()));
        List<Number> ids = JsonPath.read(json, "$.arrangements[?(@.recipient == '" + recipient + "' && @.status == 'CONFIRMED')].id");
        return ids.getLast().longValue();
    }

    private static String row(Long id, Long version, String recipient, String role, String rate) {
        return "{" + (id == null ? "" : "\"id\":" + id + ",") + (version == null ? "" : "\"version\":" + version + ",")
                + "\"recipient\":\"" + recipient + "\"," + (role == null ? "" : "\"role\":\"" + role + "\",") + "\"ratePct\":" + rate + "}";
    }

    private ResultActions save(long projectId, boolean confirm, Boolean exception, String... rows) throws Exception {
        String body = "{\"confirm\":" + confirm + (exception == null ? "" : ",\"acceptPolicyException\":" + exception)
                + ",\"arrangements\":[" + String.join(",", rows) + "]}";
        return mockMvc.perform(put("/api/v1/projects/" + projectId + "/commissions").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions pay(long id, UUID requestId, String amount, LocalDate date) throws Exception {
        return mockMvc.perform(post("/api/v1/commissions/" + id + "/payments").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"%s\",\"paymentDate\":\"%s\",\"amount\":%s,\"paymentMethodId\":%d,\"reference\":\" \"}"
                        .formatted(requestId, date, amount, cash)));
    }

    private ResultActions voidPayment(long id, long paymentId, String reason, long version) throws Exception {
        return mockMvc.perform(post("/api/v1/commissions/" + id + "/payments/" + paymentId + "/void").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"%s\",\"version\":%d}".formatted(reason, version)));
    }

    private ResultActions voidCommission(long id, String reason, long version) throws Exception {
        return mockMvc.perform(post("/api/v1/commissions/" + id + "/void").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"%s\",\"version\":%d}".formatted(reason, version)));
    }

    private ResultActions commission(long id) throws Exception {
        return mockMvc.perform(get("/api/v1/commissions/" + id)).andExpect(status().isOk());
    }

    private String projectJson(long projectId) throws Exception {
        return mockMvc.perform(get("/api/v1/projects/" + projectId + "/commissions")).andReturn().getResponse().getContentAsString();
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private static long id(String json, int index) {
        return ((Number) JsonPath.read(json, "$.arrangements[" + index + "].id")).longValue();
    }

    private static long version(String json, int index) {
        return ((Number) JsonPath.read(json, "$.arrangements[" + index + "].version")).longValue();
    }

    private long count(long projectId) {
        return jdbc.queryForObject("SELECT count(*) FROM commission_arrangements WHERE project_id = ?", Long.class, projectId);
    }

    private List<String> activity(long projectId) {
        return jdbc.queryForList("SELECT message FROM activity_log WHERE project_id = ? AND kind = 'COMMISSION'", String.class, projectId);
    }

    private static double delta(String before, String after, String path) {
        return ((Number) JsonPath.read(after, path)).doubleValue() - ((Number) JsonPath.read(before, path)).doubleValue();
    }
}
