package com.sondurukedella.integration.api;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// The reports against a known fixture built through the real APIs of every module, so each figure can
// be checked by hand. Company-wide figures are compared before/after the fixture (deltas), so other
// rows in the database don't matter. Rolls back.
//
// Fixture, construction project P1 (contract 1,000,000), IN_PROGRESS:
//   installments 600,000 due 2026-10-01 (billed 2026-10-20) and 400,000 due 2027-06-30 (unbilled)
//   applied delay deduction 20,000 on the first -> revenue recorded 580,000
//   receipts 250,000 (active) and 50,000 (voided); write-off 100,000 -> outstanding 230,000
//   manual project expense 30,000 (10,000 paid); company expense 5,000
//   stock: 100 bags bought at 1,000 (not a cost), 30 issued, 5 returned -> materials 25,000, stock +75,000
//   labour: two present days at 3,000 in the week of 2026-10-19 (not paid, live) -> 6,000
//   commission: Nimal 2% confirmed (20,000, 5,000 paid), Kamal 1% confirmed then voided, Sunil 1% draft
//   costs 30,000 + 25,000 + 6,000 + 20,000 = 81,000; profit 580,000 − 100,000 − 81,000 = 399,000 (68.79%)
// Design project P2 (contract 500,000, PLANNING): commission 20% confirmed (100,000), nothing billed.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMIN")
class ReportApiIntegrationTest {

    private static final String REPORTS = "/api/v1/reports";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private final LocalDate today = LocalDate.now(ClockConfig.BUSINESS_ZONE);
    private long cash;
    private long client;
    private long p1;
    private long p2;
    private long foundation;

    @BeforeEach
    void setUp() {
        cash = jdbc.queryForObject("SELECT id FROM payment_methods WHERE name = 'Cash'", Long.class);
        client = jdbc.queryForObject("INSERT INTO clients (name, phone) VALUES ('Report Client', '0710000000') RETURNING id", Long.class);
    }

    @Test
    void theProjectReportSeparatesContractBillingReceiptsAndCosts() throws Exception {
        buildFixture();
        mockMvc.perform(get(REPORTS + "/projects/" + p1 + "/financial"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.clientName").value("Report Client"))
                .andExpect(jsonPath("$.project.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.contractValue").value(1000000.00))
                .andExpect(jsonPath("$.revenue.billedAmount").value(600000.00))
                .andExpect(jsonPath("$.revenue.deductions").value(20000.00))
                .andExpect(jsonPath("$.revenue.revenueRecorded").value(580000.00))
                .andExpect(jsonPath("$.revenue.received").value(250000.00))
                .andExpect(jsonPath("$.revenue.writtenOff").value(100000.00))
                .andExpect(jsonPath("$.revenue.outstanding").value(230000.00))
                .andExpect(jsonPath("$.revenue.overdue").value(230000.00))
                .andExpect(jsonPath("$.revenue.unbilled").value(400000.00))
                .andExpect(jsonPath("$.costs.manualExpenses").value(30000.00))
                .andExpect(jsonPath("$.costs.materials").value(25000.00))
                .andExpect(jsonPath("$.costs.labour").value(6000.00))
                .andExpect(jsonPath("$.costs.labourOpenWeeks").value(6000.00))
                .andExpect(jsonPath("$.costs.commission").value(20000.00))
                .andExpect(jsonPath("$.costs.total").value(81000.00))
                .andExpect(jsonPath("$.commission.paid").value(5000.00))
                .andExpect(jsonPath("$.commission.outstanding").value(15000.00))
                .andExpect(jsonPath("$.commission.draftAmount").value(10000.00))
                .andExpect(jsonPath("$.profit.profit").value(399000.00))
                .andExpect(jsonPath("$.profit.marginPct").value(68.79))
                .andExpect(jsonPath("$.profit.basis").value("ESTIMATED"))
                .andExpect(jsonPath("$.notIncluded.length()").value(1));

        // design project: commission only, nothing billed, so no margin
        mockMvc.perform(get(REPORTS + "/projects/" + p2 + "/financial"))
                .andExpect(jsonPath("$.costs.commission").value(100000.00))
                .andExpect(jsonPath("$.costs.labour").value(0))
                .andExpect(jsonPath("$.costs.total").value(100000.00))
                .andExpect(jsonPath("$.profit.profit").value(-100000.00))
                .andExpect(jsonPath("$.profit.marginPct").doesNotExist());
    }

    @Test
    void aCompletedProjectHasARecordedProfit() throws Exception {
        buildFixture();
        long version = jdbc.queryForObject("SELECT version FROM projects WHERE id = ?", Long.class, p1);
        mockMvc.perform(put("/api/v1/projects/" + p1 + "/status").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"version\":" + version + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(get(REPORTS + "/projects/" + p1 + "/financial")).andExpect(jsonPath("$.profit.basis").value("RECORDED"));
    }

    @Test
    void theCostBreakdownAddsUpToTheProjectCostsToTheCent() throws Exception {
        buildFixture();
        String json = mockMvc.perform(get(REPORTS + "/projects/" + p1 + "/cost-breakdown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].category").value("MANUAL_EXPENSES"))
                .andExpect(jsonPath("$.categories[0].amount").value(30000.00))
                .andExpect(jsonPath("$.categories[0].lines[0].label").value("Machinery Rental"))
                .andExpect(jsonPath("$.categories[0].lines[0].count").value(1))
                .andExpect(jsonPath("$.categories[1].category").value("MATERIALS"))
                .andExpect(jsonPath("$.categories[1].amount").value(25000.00))
                .andExpect(jsonPath("$.categories[3].category").value("LABOUR"))
                .andExpect(jsonPath("$.categories[3].lines[2].amount").value(6000.00))
                .andExpect(jsonPath("$.categories[4].category").value("COMMISSION"))
                .andExpect(jsonPath("$.categories[4].lines.length()").value(1)) // Kamal (void) and Sunil (draft) are not costs
                .andExpect(jsonPath("$.categories[4].lines[0].label").value("Nimal (Commission, 2%)"))
                .andExpect(jsonPath("$.total").value(81000.00))
                .andReturn().getResponse().getContentAsString();

        List<Number> categories = JsonPath.read(json, "$.categories[*].amount");
        BigDecimal sum = categories.stream().map(n -> new BigDecimal(n.toString())).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(new BigDecimal(JsonPath.read(json, "$.total").toString()));
        for (int c = 0; c < 5; c++) {
            List<Number> lines = JsonPath.read(json, "$.categories[" + c + "].lines[*].amount");
            BigDecimal lineSum = lines.stream().map(n -> new BigDecimal(n.toString())).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(lineSum).isEqualByComparingTo(new BigDecimal(JsonPath.read(json, "$.categories[" + c + "].amount").toString()));
        }
        String financial = mockMvc.perform(get(REPORTS + "/projects/" + p1 + "/financial")).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(financial, "$.costs.total").doubleValue()).isEqualTo(JsonPath.<Number>read(json, "$.total").doubleValue());
    }

    @Test
    void anEmptyProjectReportsZerosAndAMissingProjectIs404() throws Exception {
        long empty = project("CONSTRUCTION", "Empty", "250000", "[{\"description\":\"All\",\"dueDate\":\"2027-01-31\",\"amount\":250000}]");
        mockMvc.perform(get(REPORTS + "/projects/" + empty + "/financial"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue.revenueRecorded").value(0))
                .andExpect(jsonPath("$.revenue.unbilled").value(250000.00))
                .andExpect(jsonPath("$.costs.total").value(0))
                .andExpect(jsonPath("$.profit.profit").value(0))
                .andExpect(jsonPath("$.profit.marginPct").doesNotExist());
        mockMvc.perform(get(REPORTS + "/projects/" + empty + "/cost-breakdown"))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.categories.length()").value(5))
                .andExpect(jsonPath("$.categories[0].lines.length()").value(0));
        mockMvc.perform(get(REPORTS + "/projects/999999999/financial")).andExpect(status().isNotFound());
        mockMvc.perform(get(REPORTS + "/projects/999999999/cost-breakdown")).andExpect(status().isNotFound());
        mockMvc.perform(get(REPORTS + "/projects/abc/financial")).andExpect(status().isBadRequest());
    }

    @Test
    void theDashboardMovesByExactlyTheFixture() throws Exception {
        String before = body(REPORTS + "/dashboard");
        buildFixture();
        String after = body(REPORTS + "/dashboard");

        assertThat(delta(before, after, "$.projects.active")).isEqualTo(1);
        assertThat(delta(before, after, "$.projects.activeByType.CONSTRUCTION")).isEqualTo(1);
        assertThat(delta(before, after, "$.projects.byStatus.IN_PROGRESS")).isEqualTo(1);
        assertThat(delta(before, after, "$.projects.byStatus.PLANNING")).isEqualTo(1);
        assertThat(delta(before, after, "$.projects.activeContractValue")).isEqualTo(1000000);
        assertThat(delta(before, after, "$.revenue.revenueRecorded")).isEqualTo(580000);
        assertThat(delta(before, after, "$.revenue.paymentsReceived")).isEqualTo(250000);
        assertThat(delta(before, after, "$.revenue.writtenOff")).isEqualTo(100000);
        assertThat(delta(before, after, "$.receivables.outstanding")).isEqualTo(230000);
        assertThat(delta(before, after, "$.receivables.overdue")).isEqualTo(230000);
        assertThat(delta(before, after, "$.projectResults.projectCosts")).isEqualTo(181000);
        assertThat(delta(before, after, "$.projectResults.projectProfit")).isEqualTo(299000);
        assertThat(delta(before, after, "$.projectResults.companyExpenses")).isEqualTo(5000);
        assertThat(delta(before, after, "$.payables.expenses")).isEqualTo(25000);
        assertThat(delta(before, after, "$.payables.commission")).isEqualTo(115000);
        assertThat(delta(before, after, "$.inventoryValue")).isEqualTo(75000);

        // October 2026 is in the series while it is in the current financial year
        List<Object> october = JsonPath.read(after, "$.monthly[?(@.month == '2026-10-01')]");
        if (!october.isEmpty()) {
            assertThat(monthDelta(before, after, "revenueRecorded")).isEqualTo(580000);
            assertThat(monthDelta(before, after, "paymentsReceived")).isEqualTo(250000);
            assertThat(monthDelta(before, after, "manualExpenses")).isEqualTo(35000);
        }
        assertThat(JsonPath.<String>read(after, "$.asOf")).isEqualTo(today.toString());
    }

    @Test
    void theFinancialSummaryUsesEachFiguresOwnDate() throws Exception {
        String october = "?from=2026-10-01&to=2026-10-31";
        String before = body(REPORTS + "/financial-summary" + october);
        String commissionBefore = body(REPORTS + "/financial-summary?from=" + today + "&to=" + today);
        buildFixture();
        String after = body(REPORTS + "/financial-summary" + october);
        String commissionAfter = body(REPORTS + "/financial-summary?from=" + today + "&to=" + today);

        assertThat(delta(before, after, "$.revenue.installmentsBilled")).isEqualTo(1);
        assertThat(delta(before, after, "$.revenue.billedAmount")).isEqualTo(600000);
        assertThat(delta(before, after, "$.revenue.deductions")).isEqualTo(20000);
        assertThat(delta(before, after, "$.revenue.revenueRecorded")).isEqualTo(580000);
        assertThat(delta(before, after, "$.revenue.receipts")).isEqualTo(1); // the voided one doesn't count
        assertThat(delta(before, after, "$.revenue.received")).isEqualTo(250000);
        assertThat(delta(before, after, "$.revenue.writtenOff")).isEqualTo(100000);
        assertThat(delta(before, after, "$.expenses.projectExpenses")).isEqualTo(30000);
        assertThat(delta(before, after, "$.expenses.companyExpenses")).isEqualTo(5000);
        assertThat(delta(before, after, "$.materials.issued")).isEqualTo(30000);
        assertThat(delta(before, after, "$.materials.returned")).isEqualTo(5000);
        assertThat(delta(before, after, "$.materials.net")).isEqualTo(25000);
        assertThat(delta(before, after, "$.labour.labourCost")).isEqualTo(6000);
        assertThat(delta(before, after, "$.labour.grossPay")).isEqualTo(6000);
        assertThat(delta(before, after, "$.labour.paid")).isEqualTo(0); // nothing paid: not a cost question
        assertThat(delta(before, after, "$.position.inventoryValue")).isEqualTo(75000);
        assertThat(delta(before, after, "$.position.receivablesOutstanding")).isEqualTo(230000);
        // commissions are confirmed and paid today; the voided one is not a cost
        assertThat(delta(commissionBefore, commissionAfter, "$.commission.confirmed")).isEqualTo(120000);
        assertThat(delta(commissionBefore, commissionAfter, "$.commission.confirmedCount")).isEqualTo(2);
        assertThat(delta(commissionBefore, commissionAfter, "$.commission.paid")).isEqualTo(5000);
        assertThat(delta(commissionBefore, commissionAfter, "$.position.commissionOutstanding")).isEqualTo(115000);
    }

    @Test
    void dateBoundariesAreInclusiveAndExact() throws Exception {
        String billingDayBefore = body(REPORTS + "/financial-summary?from=2026-10-20&to=2026-10-20");
        String afterBillingBefore = body(REPORTS + "/financial-summary?from=2026-10-21&to=2026-10-31");
        buildFixture();
        String billingDay = body(REPORTS + "/financial-summary?from=2026-10-20&to=2026-10-20");
        String afterBilling = body(REPORTS + "/financial-summary?from=2026-10-21&to=2026-10-31");

        assertThat(delta(billingDayBefore, billingDay, "$.revenue.revenueRecorded")).isEqualTo(580000);
        assertThat(delta(billingDayBefore, billingDay, "$.revenue.received")).isEqualTo(250000);
        assertThat(delta(billingDayBefore, billingDay, "$.revenue.writtenOff")).isEqualTo(0); // written off on the 22nd
        assertThat(delta(afterBillingBefore, afterBilling, "$.revenue.revenueRecorded")).isEqualTo(0);
        assertThat(delta(afterBillingBefore, afterBilling, "$.revenue.writtenOff")).isEqualTo(100000);
        // no Monday between Tuesday the 20th and the 20th itself: no payroll week
        assertThat(JsonPath.<Integer>read(billingDay, "$.labour.weeks")).isZero();
        assertThat(JsonPath.<Object>read(billingDay, "$.labour.firstWeek")).isNull();
    }

    @Test
    void invalidRangesAreRejected() throws Exception {
        mockMvc.perform(get(REPORTS + "/financial-summary")).andExpect(status().isBadRequest());
        mockMvc.perform(get(REPORTS + "/financial-summary?from=2026-10-31&to=2026-10-01")).andExpect(status().isBadRequest());
        mockMvc.perform(get(REPORTS + "/financial-summary?from=2025-01-01&to=2026-10-01")).andExpect(status().isBadRequest());
        mockMvc.perform(get(REPORTS + "/financial-summary?from=2026-13-01&to=2026-10-01")).andExpect(status().isBadRequest());
        mockMvc.perform(get(REPORTS + "/payroll?from=2026-10-31&to=2026-10-01")).andExpect(status().isBadRequest());
        mockMvc.perform(get(REPORTS + "/payroll?from=2026-10-01")).andExpect(status().isBadRequest());
    }

    @Test
    void theReceivablesReportShowsAgingFromTheRevenueModule() throws Exception {
        buildFixture();
        long days = ChronoUnit.DAYS.between(LocalDate.of(2026, 10, 1), today);
        String bucket = days <= 30 ? "DAYS_1_30" : days <= 60 ? "DAYS_31_60" : days <= 90 ? "DAYS_61_90" : "DAYS_OVER_90";
        mockMvc.perform(get(REPORTS + "/receivables").param("projectId", String.valueOf(p1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.installments").value(1)) // the unbilled one is not a receivable
                .andExpect(jsonPath("$.totals.outstanding").value(230000.00))
                .andExpect(jsonPath("$.totals.outstandingByAging." + bucket).value(230000.00))
                .andExpect(jsonPath("$.rows.content[0].installmentId").value(foundation))
                .andExpect(jsonPath("$.rows.content[0].projectCode").isString())
                .andExpect(jsonPath("$.rows.content[0].clientName").value("Report Client"))
                .andExpect(jsonPath("$.rows.content[0].net").value(580000.00))
                .andExpect(jsonPath("$.rows.content[0].received").value(250000.00))
                .andExpect(jsonPath("$.rows.content[0].writtenOff").value(100000.00))
                .andExpect(jsonPath("$.rows.content[0].status").value("OVERDUE"))
                .andExpect(jsonPath("$.rows.content[0].daysOverdue").value(days))
                .andExpect(jsonPath("$.rows.content[0].aging").value(bucket));
        mockMvc.perform(get(REPORTS + "/receivables").param("projectId", String.valueOf(p1)).param("aging", "NOT_DUE"))
                .andExpect(jsonPath("$.totals.installments").value(0));
        mockMvc.perform(get(REPORTS + "/receivables").param("projectId", String.valueOf(p1)).param("status", "PAID").param("openOnly", "false"))
                .andExpect(jsonPath("$.totals.installments").value(0));
        mockMvc.perform(get(REPORTS + "/receivables").param("status", "UNBILLED")).andExpect(status().isBadRequest());
        mockMvc.perform(get(REPORTS + "/receivables").param("sort", "amount")).andExpect(status().isBadRequest());
        mockMvc.perform(get(REPORTS + "/receivables").param("size", "101")).andExpect(status().isBadRequest());
        mockMvc.perform(get(REPORTS + "/receivables").param("projectId", "999999999")).andExpect(status().isNotFound());
        mockMvc.perform(get(REPORTS + "/receivables").param("aging", "OLD")).andExpect(status().isBadRequest());
        mockMvc.perform(get(REPORTS + "/receivables").param("size", "1").param("sort", "dueDate,desc")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.size").value(1));
    }

    @Test
    void inventoryValuationIsTheFifoLayerValueNotPurchases() throws Exception {
        String before = body(REPORTS + "/inventory-valuation");
        buildFixture();
        String after = body(REPORTS + "/inventory-valuation?q=RPT-CEM");
        // bought 100,000 worth, issued 30,000, 5,000 came back: 75,000 is left in the layers
        assertThat(delta(before, after, "$.stockValue")).isEqualTo(75000);
        assertThat(JsonPath.<Number>read(after, "$.rows.content[0].stockValue").doubleValue()).isEqualTo(75000.0);
        assertThat(JsonPath.<Number>read(after, "$.rows.content[0].quantityOnHand").doubleValue()).isEqualTo(75.0);
    }

    @Test
    void thePayrollReportCountsGrossWagesNotAdvancesOrPayments() throws Exception {
        String range = "?from=2026-10-19&to=2026-10-25";
        String before = body(REPORTS + "/payroll" + range);
        buildFixture();
        String after = body(REPORTS + "/payroll" + range);
        assertThat(JsonPath.<Integer>read(after, "$.totals.weeks")).isEqualTo(1);
        assertThat(JsonPath.<String>read(after, "$.weeks[0].weekStart")).isEqualTo("2026-10-19");
        assertThat(delta(before, after, "$.totals.labourCost")).isEqualTo(6000);
        assertThat(delta(before, after, "$.weeks[0].grossPay")).isEqualTo(6000);
    }

    @Test
    void readingReportsChangesNothing() throws Exception {
        buildFixture();
        entityManager.flush();
        String before = snapshot();
        for (String url : List.of(REPORTS + "/dashboard", REPORTS + "/financial-summary?from=2026-10-01&to=2026-10-31",
                REPORTS + "/projects/" + p1 + "/financial", REPORTS + "/projects/" + p1 + "/cost-breakdown", REPORTS + "/receivables",
                REPORTS + "/inventory-valuation", REPORTS + "/payroll?from=2026-10-19&to=2026-10-25")) {
            mockMvc.perform(get(url)).andExpect(status().isOk());
        }
        entityManager.flush();
        assertThat(snapshot()).isEqualTo(before);
    }

    // ---------------------------------------------------------------- fixture

    private void buildFixture() throws Exception {
        p1 = project("CONSTRUCTION", "Report House", "1000000", """
                [{"description":"Foundation","dueDate":"2026-10-01","amount":600000},
                 {"description":"Handover","dueDate":"2027-06-30","amount":400000}]""");
        ApiFixtures.setProjectStatus(mockMvc, p1, "IN_PROGRESS", 0);
        foundation = jdbc.queryForObject("SELECT id FROM installments WHERE project_id = ? AND description = 'Foundation'", Long.class, p1);

        // revenue
        send(post("/api/v1/projects/" + p1 + "/installments/" + foundation + "/bill"), "{\"billedOn\":\"2026-10-20\",\"version\":0}");
        String receipt = "{\"requestId\":\"%s\",\"paymentDate\":\"2026-10-20\",\"amount\":%s,\"paymentMethodId\":%d}";
        send(post("/api/v1/projects/" + p1 + "/installments/" + foundation + "/receipts"), receipt.formatted(UUID.randomUUID(), "250000", cash));
        String bounced = send(post("/api/v1/projects/" + p1 + "/installments/" + foundation + "/receipts"),
                receipt.formatted(UUID.randomUUID(), "50000", cash));
        send(post("/api/v1/projects/" + p1 + "/receipts/" + id(bounced) + "/void"), "{\"reason\":\"Cheque bounced\",\"version\":0}");
        send(post("/api/v1/projects/" + p1 + "/installments/" + foundation + "/write-offs"),
                "{\"date\":\"2026-10-22\",\"amount\":100000,\"reason\":\"Agreed discount\"}");
        jdbc.update("""
                INSERT INTO delay_deduction_terms (project_id, contract_deadline, rule, fixed_amount, actual_completion,
                                                   applied_amount, applied_on, applied_installment_id)
                VALUES (?, DATE '2026-09-30', 'FIXED_AMOUNT', 20000, DATE '2026-10-15', 20000, DATE '2026-10-20', ?)""", p1, foundation);

        // manual expenses
        long machinery = jdbc.queryForObject("SELECT id FROM expense_categories WHERE expense_group = 'CONSTRUCTION' AND name = 'Machinery Rental'", Long.class);
        long marketing = jdbc.queryForObject("SELECT id FROM expense_categories WHERE expense_group = 'COMPANY' AND name = 'Marketing'", Long.class);
        String expense = send(post("/api/v1/expenses"), """
                {"date":"2026-10-20","scope":"PROJECT","projectId":%d,"categoryId":%d,"description":"Excavator hire",
                 "payee":"Rajarata Hire","amount":30000}""".formatted(p1, machinery));
        send(post("/api/v1/expenses/" + id(expense) + "/payments"),
                "{\"requestId\":\"%s\",\"paymentDate\":\"2026-10-21\",\"amount\":10000,\"paymentMethodId\":%d}".formatted(UUID.randomUUID(), cash));
        send(post("/api/v1/expenses"), """
                {"date":"2026-10-20","scope":"COMPANY","categoryId":%d,"description":"Banner","payee":"Print Shop","amount":5000}"""
                .formatted(marketing));

        // stock: a purchase (not a cost), an issue and a return
        long bag = jdbc.queryForObject("SELECT id FROM units WHERE name = 'bag'", Long.class);
        long cement = id(send(post("/api/v1/inventory/materials"), "{\"sku\":\"RPT-CEM\",\"name\":\"Report Cement\",\"category\":\"Building\",\"unitId\":%d}".formatted(bag)));
        send(post("/api/v1/inventory/receipts"), "{\"requestId\":\"%s\",\"materialId\":%d,\"date\":\"2026-10-20\",\"quantity\":100,\"unitCost\":1000}"
                .formatted(UUID.randomUUID(), cement));
        long issue = id(send(post("/api/v1/inventory/issues"), "{\"requestId\":\"%s\",\"materialId\":%d,\"projectId\":%d,\"date\":\"2026-10-20\",\"quantity\":30}"
                .formatted(UUID.randomUUID(), cement, p1)));
        send(post("/api/v1/inventory/returns"), "{\"issueId\":%d,\"requestId\":\"%s\",\"date\":\"2026-10-21\",\"quantity\":5}".formatted(issue, UUID.randomUUID()));

        // labour: two present days, week not paid
        long worker = id(send(post("/api/v1/employees"),
                "{\"name\":\"Report Worker\",\"role\":\"Labourer\",\"dailyWage\":3000,\"overtimeRate\":500,\"joinedOn\":\"2026-01-05\"}"));
        for (String day : List.of("2026-10-19", "2026-10-20")) {
            entityManager.flush();
            List<String> others = jdbc.queryForList("SELECT format('{\"employeeId\":%s,\"status\":\"%s\"%s}', employee_id, status, "
                    + "CASE WHEN project_id IS NULL THEN '' ELSE ',\"projectId\":' || project_id END) FROM attendance "
                    + "WHERE work_date = ?::date AND employee_id <> ?", String.class, day, worker);
            List<String> entries = new ArrayList<>(others);
            entries.add("{\"employeeId\":%d,\"status\":\"PRESENT\",\"projectId\":%d}".formatted(worker, p1));
            send(put("/api/v1/attendance/" + day), "{\"entries\":[" + String.join(",", entries) + "]}");
        }

        // commission: confirmed, voided, draft
        String set = send(put("/api/v1/projects/" + p1 + "/commissions"),
                "{\"confirm\":true,\"arrangements\":[{\"recipient\":\"Nimal\",\"ratePct\":2},{\"recipient\":\"Kamal\",\"ratePct\":1}]}");
        long nimal = ((Number) JsonPath.read(set, "$.arrangements[0].id")).longValue();
        long nimalVersion = ((Number) JsonPath.read(set, "$.arrangements[0].version")).longValue();
        long kamal = ((Number) JsonPath.read(set, "$.arrangements[1].id")).longValue();
        long kamalVersion = ((Number) JsonPath.read(set, "$.arrangements[1].version")).longValue();
        send(post("/api/v1/commissions/" + kamal + "/void"), "{\"reason\":\"Not agreed\",\"version\":" + kamalVersion + "}");
        send(put("/api/v1/projects/" + p1 + "/commissions"), "{\"confirm\":false,\"arrangements\":[{\"id\":%d,\"version\":%d,\"recipient\":\"Nimal\",\"ratePct\":2},{\"recipient\":\"Sunil\",\"ratePct\":1}]}"
                .formatted(nimal, nimalVersion));
        send(post("/api/v1/commissions/" + nimal + "/payments"),
                "{\"requestId\":\"%s\",\"paymentDate\":\"%s\",\"amount\":5000,\"paymentMethodId\":%d}".formatted(UUID.randomUUID(), today, cash));

        // design project with a 20% commission and nothing billed
        p2 = project("DESIGN", "Report Plan", "500000", "[{\"description\":\"All\",\"dueDate\":\"2027-03-31\",\"amount\":500000}]");
        send(put("/api/v1/projects/" + p2 + "/commissions"), "{\"confirm\":true,\"arrangements\":[{\"recipient\":\"Designer\",\"ratePct\":20}]}");
    }

    private long project(String type, String name, String contract, String schedule) throws Exception {
        return id(send(post("/api/v1/projects"), """
                {"name":"%s","type":"%s","clientId":%d,"contractValue":%s,"startDate":"2026-01-01",
                 "expectedCompletion":"2027-12-31","schedule":%s}""".formatted(name, type, client, contract, schedule)));
    }

    private String send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String json) throws Exception {
        ResultActions result = mockMvc.perform(request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
        int code = result.andReturn().getResponse().getStatus();
        assertThat(code).as(result.andReturn().getResponse().getContentAsString()).isBetween(200, 201);
        return result.andReturn().getResponse().getContentAsString();
    }

    private String body(String url) throws Exception {
        return mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static long id(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private static double delta(String before, String after, String path) {
        return ((Number) JsonPath.read(after, path)).doubleValue() - ((Number) JsonPath.read(before, path)).doubleValue();
    }

    private static double monthDelta(String before, String after, String field) {
        List<Number> b = JsonPath.read(before, "$.monthly[?(@.month == '2026-10-01')]." + field);
        List<Number> a = JsonPath.read(after, "$.monthly[?(@.month == '2026-10-01')]." + field);
        return a.getFirst().doubleValue() - b.getFirst().doubleValue();
    }

    // every table a report could touch, as one checksum
    private String snapshot() {
        StringBuilder all = new StringBuilder();
        for (String table : List.of("projects", "installments", "client_payments", "receivable_write_offs", "delay_deduction_terms",
                "expenses", "expense_payments", "materials", "stock_movements", "stock_cost_layers", "stock_cost_allocations",
                "employees", "attendance", "payroll_lines", "payroll_payments", "commission_arrangements", "commission_payments",
                "commission_policy", "company_profile", "activity_log", "app_users", "security_events")) {
            all.append(table).append('=').append(jdbc.queryForObject(
                    "SELECT md5(coalesce(string_agg(t::text, '|' ORDER BY t::text), '')) FROM " + table + " t", String.class)).append(';');
        }
        return all.toString();
    }
}
