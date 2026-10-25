package com.sondurukedella.integration.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sondurukedella.config.ClockConfig;
import jakarta.persistence.EntityManager;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// Expenses against the real schema with a fixed clock; each test rolls back. Concurrent payments
// and real-commit rollback are in ExpenseConcurrencyIntegrationTest.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMIN")
class ExpenseApiIntegrationTest {

    // 19:00 UTC on the 24th is already the 25th in Colombo
    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-24T19:00:00Z"), ClockConfig.BUSINESS_ZONE);
        }
    }

    private static final String URL = "/api/v1/expenses";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private long construction;
    private long design;
    private long cancelled;
    private long materials;
    private long machinery;
    private long labour;
    private long printing;
    private long marketing;
    private long cube;
    private long cash;

    @BeforeEach
    void setUp() throws Exception {
        long client = jdbc.queryForObject("INSERT INTO clients (name, phone) VALUES ('Expense Client', '0710000000') RETURNING id", Long.class);
        construction = project(client, "CONSTRUCTION", "House");
        design = project(client, "DESIGN", "Plan");
        cancelled = project(client, "CONSTRUCTION", "Dropped");
        mockMvc.perform(put("/api/v1/projects/" + cancelled + "/status").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CANCELLED\",\"version\":0}")).andExpect(status().isOk());
        materials = category("CONSTRUCTION", "Materials");
        machinery = category("CONSTRUCTION", "Machinery Rental");
        labour = category("CONSTRUCTION", "Labour");
        printing = category("DESIGN", "Printing");
        marketing = category("COMPANY", "Marketing");
        cube = jdbc.queryForObject("SELECT id FROM units WHERE name = 'cube'", Long.class);
        cash = jdbc.queryForObject("SELECT id FROM payment_methods WHERE name = 'Cash'", Long.class);
    }

    @Test
    void aProjectExpenseIsRecordedAgainstItsProject() throws Exception {
        create(projectExpense(construction, machinery, "45000") + ",\"machineType\":\"JCB 3CX\",\"usageFrom\":\"2026-10-10\","
                + "\"usageTo\":\"2026-10-12\",\"invoiceNo\":\"HR-778\",\"dueDate\":\"2026-11-10\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scope").value("PROJECT"))
                .andExpect(jsonPath("$.projectId").value(construction))
                .andExpect(jsonPath("$.group").value("CONSTRUCTION"))
                .andExpect(jsonPath("$.category").value("Machinery Rental"))
                .andExpect(jsonPath("$.machineType").value("JCB 3CX"))
                .andExpect(jsonPath("$.paid").value(0))
                .andExpect(jsonPath("$.outstanding").value(45000.00))
                .andExpect(jsonPath("$.status").value("UNPAID"));
        assertThat(activity(construction)).containsExactly("Expense recorded: Machinery Rental - Excavator hire (LKR 45,000.00).");
    }

    @Test
    void aCompanyExpenseHasNoProjectAndPaidInFullIsSavedTogether() throws Exception {
        long id = id(create(companyExpense("18500") + ",\"initialPayment\":" + payment(UUID.randomUUID(), "18500") + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scope").value("COMPANY"))
                .andExpect(jsonPath("$.projectId").doesNotExist())
                .andExpect(jsonPath("$.group").value("COMPANY"))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.outstanding").value(0)));

        mockMvc.perform(get(URL + "/" + id)).andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.payments[0].method").value("Cash"));
        // company costs never show up on a project
        mockMvc.perform(get(URL).param("projectId", String.valueOf(construction))).andExpect(jsonPath("$.totalElements").value(0));
        assertThat(jdbc.queryForObject("SELECT project_id FROM expenses WHERE id = ?", Long.class, id)).isNull();
    }

    @Test
    void aMaterialsLineWorksOutItsAmount() throws Exception {
        create("""
                {"date":"2026-10-20","scope":"PROJECT","projectId":%d,"categoryId":%d,"description":"River sand",
                 "payee":"Mihintale Hardware","quantity":12.5,"unitId":%d,"unitPrice":4333.3333}""".formatted(construction, materials, cube))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(54166.67))
                .andExpect(jsonPath("$.unit").value("cube"))
                .andExpect(jsonPath("$.quantity").value(12.5));
        create("""
                {"date":"2026-10-20","scope":"PROJECT","projectId":%d,"categoryId":%d,"description":"River sand",
                 "payee":"Mihintale Hardware","quantity":2,"unitId":%d,"unitPrice":100,"amount":250}""".formatted(construction, materials, cube))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("quantity x unit price")));
    }

    @Test
    void systemManagedCostsCannotBeEnteredByHand() throws Exception {
        create(projectExpense(construction, labour, "30000") + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("added automatically")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses", Long.class)).isZero();
    }

    @Test
    void theCategoryMustFitTheProjectTypeOrCompanyScope() throws Exception {
        // a design category on a construction project, a project category on a company expense
        create(projectExpense(construction, printing, "1000") + "}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("this expense needs a Construction category")));
        create(projectExpense(design, printing, "1000") + "}").andExpect(status().isCreated());
        create("""
                {"date":"2026-10-20","scope":"COMPANY","categoryId":%d,"description":"Sand","payee":"X","amount":1}""".formatted(materials))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("needs a Company category")));
        create(projectExpense(construction, Long.MAX_VALUE, "1000") + "}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Choose a category from the Settings list."));
    }

    @Test
    void aRemovedCategoryCannotBeChosenForNewExpenses() throws Exception {
        jdbc.update("UPDATE expense_categories SET active = false WHERE id = ?", machinery);
        entityManager.clear();

        create(projectExpense(construction, machinery, "1000") + "}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("removed from the Settings list")));
    }

    @Test
    void anExpenseKeepsItsCategoryWhenEditedAfterTheCategoryWasRemoved() throws Exception {
        long id = id(create(projectExpense(construction, machinery, "1000") + "}"));
        jdbc.update("UPDATE expense_categories SET active = false WHERE id = ?", machinery);
        entityManager.clear();

        update(id, projectExpense(construction, machinery, "1200") + ",\"version\":0}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(1200.00));
    }

    @Test
    void theProjectMustExistAndNotBeCancelled() throws Exception {
        create(projectExpense(Long.MAX_VALUE, machinery, "1000") + "}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The selected project does not exist."));
        create(projectExpense(cancelled, machinery, "1000") + "}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("is cancelled")));
        create("""
                {"date":"2026-10-20","scope":"PROJECT","categoryId":%d,"description":"X","payee":"X","amount":1}""".formatted(machinery))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("Choose the project")));
        create("""
                {"date":"2026-10-20","scope":"COMPANY","projectId":%d,"categoryId":%d,"description":"X","payee":"X","amount":1}"""
                .formatted(construction, marketing))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("not assigned to a project")));
    }

    @Test
    void invalidDatesAndAmountsAreRejected() throws Exception {
        create(projectExpense(construction, machinery, "1000")
                + ",\"usageFrom\":\"2026-10-12\",\"usageTo\":\"2026-10-10\"}").andExpect(status().isBadRequest());
        create(projectExpense(construction, machinery, "0") + "}").andExpect(status().isBadRequest());
        create(projectExpense(construction, machinery, "-10") + "}").andExpect(status().isBadRequest());
        create(projectExpense(construction, machinery, "1000").replace("2026-10-20", "2026-02-30") + "}")
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses", Long.class)).isZero();
    }

    @Test
    void anUnknownExpenseIs404() throws Exception {
        mockMvc.perform(get(URL + "/" + Long.MAX_VALUE)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Expense not found."));
        mockMvc.perform(get(URL + "/" + Long.MAX_VALUE + "/payments")).andExpect(status().isNotFound());
        pay(Long.MAX_VALUE, UUID.randomUUID(), "100").andExpect(status().isNotFound());
        update(Long.MAX_VALUE, companyExpense("1") + ",\"version\":0}").andExpect(status().isNotFound());
    }

    @Test
    void partialPaymentsAddUpAndCannotOverpay() throws Exception {
        long id = id(create(companyExpense("50000") + ",\"dueDate\":\"2026-11-30\"}"));

        pay(id, UUID.randomUUID(), "20000").andExpect(status().isCreated());
        expect(id, "PARTIALLY_PAID", 20000, 30000);
        pay(id, UUID.randomUUID(), "30000.01").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("More than the LKR 30,000.00 still unpaid on this expense."));
        pay(id, UUID.randomUUID(), "0").andExpect(status().isBadRequest());
        pay(id, UUID.randomUUID(), "30000").andExpect(status().isCreated());
        expect(id, "PAID", 50000, 0);
        pay(id, UUID.randomUUID(), "0.01").andExpect(status().isBadRequest());

        mockMvc.perform(get(URL + "/" + id + "/payments")).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void aResentPaymentIsNotPaidTwice() throws Exception {
        long id = id(create(companyExpense("50000") + "}"));
        UUID requestId = UUID.randomUUID();

        long paymentId = id(pay(id, requestId, "10000").andExpect(status().isCreated()));
        pay(id, requestId, "10000").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(paymentId));
        pay(id, requestId, "12000").andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM expense_payments WHERE request_id = ?", Long.class, requestId)).isEqualTo(1L);
        expect(id, "PARTIALLY_PAID", 10000, 40000);
    }

    @Test
    void anInactivePaymentMethodIsRejected() throws Exception {
        long id = id(create(companyExpense("5000") + "}"));
        long cheque = jdbc.queryForObject("SELECT id FROM payment_methods WHERE name = 'Cheque'", Long.class);
        jdbc.update("UPDATE payment_methods SET active = false WHERE id = ?", cheque);
        entityManager.clear();

        mockMvc.perform(post(URL + "/" + id + "/payments").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(payment(UUID.randomUUID(), "100").replace("\"paymentMethodId\":" + cash, "\"paymentMethodId\":" + cheque)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("payment method")));
        mockMvc.perform(post(URL + "/" + id + "/payments").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(payment(UUID.randomUUID(), "100").replace("\"paymentMethodId\":" + cash, "\"paymentMethodId\":" + Long.MAX_VALUE)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aVoidedPaymentStaysButStopsCounting() throws Exception {
        long id = id(create(companyExpense("50000") + "}"));
        long paymentId = id(pay(id, UUID.randomUUID(), "20000"));

        mockMvc.perform(post(URL + "/" + id + "/payments/" + paymentId + "/void").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Paid to the wrong supplier\",\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.voided").value(true));
        mockMvc.perform(post(URL + "/" + id + "/payments/" + paymentId + "/void").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"again\",\"version\":1}")).andExpect(status().isConflict());

        expect(id, "UNPAID", 0, 50000);
        mockMvc.perform(get(URL + "/" + id)).andExpect(jsonPath("$.payments[0].voidReason").value("Paid to the wrong supplier"));
        // the V1 trigger keeps the row from being deleted, whatever the API does
        assertThatThrownBy(() -> jdbc.update("DELETE FROM expense_payments WHERE id = ?", paymentId))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void overdueUsesTheColomboDate() throws Exception {
        // due on the 24th: overdue only because today in Colombo is already the 25th
        long id = id(create(companyExpense("8000") + ",\"dueDate\":\"2026-10-24\"}"));
        expect(id, "OVERDUE", 0, 8000);
        mockMvc.perform(get(URL).param("status", "OVERDUE")).andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.contains((int) id)));
    }

    @Test
    void editingFollowsTheAccountingRules() throws Exception {
        long id = id(create(projectExpense(construction, machinery, "45000") + "}"));
        pay(id, UUID.randomUUID(), "30000");

        update(id, projectExpense(construction, machinery, "29999.99") + ",\"version\":0}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("already been paid")));
        update(id, projectExpense(construction, machinery, "40000") + ",\"version\":7}").andExpect(status().isConflict());
        update(id, projectExpense(construction, machinery, "40000") + ",\"version\":0}").andExpect(status().isOk())
                .andExpect(jsonPath("$.outstanding").value(10000.00)).andExpect(jsonPath("$.version").value(1));

        // moving it to company level: the project's feed says so, and it leaves the project's costs
        update(id, companyExpense("40000") + ",\"version\":1}").andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").doesNotExist()).andExpect(jsonPath("$.group").value("COMPANY"));
        mockMvc.perform(get(URL).param("projectId", String.valueOf(construction))).andExpect(jsonPath("$.totalElements").value(0));
        assertThat(activity(construction)).last().asString().startsWith("Expense moved off this project");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expense_payments WHERE expense_id = ?", Long.class, id)).isEqualTo(1L);
    }

    @Test
    void filtersSeparateProjectAndCompanyCostsAndPage() throws Exception {
        create(projectExpense(construction, machinery, "1000") + "}");
        create(projectExpense(construction, machinery, "2000").replace("2026-10-20", "2026-09-15") + "}");
        create(projectExpense(design, printing, "3000") + "}");
        long company = id(create(companyExpense("4000") + ",\"initialPayment\":" + payment(UUID.randomUUID(), "4000") + "}"));

        mockMvc.perform(get(URL).param("scope", "COMPANY"))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(company));
        mockMvc.perform(get(URL).param("scope", "PROJECT"))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.content[*].scope").value(everyItem(is("PROJECT"))));
        mockMvc.perform(get(URL).param("projectId", String.valueOf(construction))).andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(URL).param("categoryId", String.valueOf(printing))).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(URL).param("from", "2026-10-01").param("to", "2026-10-31")).andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get(URL).param("status", "PAID")).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(URL).param("status", "UNPAID")).andExpect(jsonPath("$.totalElements").value(3));
        // all three project rows use the "Excavator hire" description; the company one doesn't
        mockMvc.perform(get(URL).param("q", "EXCAVATOR")).andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get(URL).param("size", "2").param("page", "1").param("sort", "amount,asc"))
                .andExpect(jsonPath("$.content[*].amount").value(org.hamcrest.Matchers.contains(3000.0, 4000.0)))
                .andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get(URL).param("sort", "payee")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("from", "2026-11-01").param("to", "2026-10-01")).andExpect(status().isBadRequest());

        mockMvc.perform(get(URL + "/summary"))
                .andExpect(jsonPath("$.count").value(4))
                .andExpect(jsonPath("$.total").value(10000.00))
                .andExpect(jsonPath("$.projectTotal").value(6000.00))
                .andExpect(jsonPath("$.companyTotal").value(4000.00))
                .andExpect(jsonPath("$.paid").value(4000.00))
                .andExpect(jsonPath("$.outstanding").value(6000.00));
    }

    @Test
    void theDatabaseItselfRefusesAMismatchedCategoryOrSystemCost() {
        // belt and braces: even bypassing the service, V1's composite key and check stop these
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO expenses (expense_date, scope, project_id, project_type, category_id, expense_group,
                                      description, payee, amount)
                VALUES (DATE '2026-10-20', 'PROJECT', ?, 'CONSTRUCTION', ?, 'CONSTRUCTION', 'x', 'x', 10)""",
                construction, printing)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void readingChangesNothing() throws Exception {
        long id = id(create(companyExpense("5000") + "}"));
        pay(id, UUID.randomUUID(), "1000");
        entityManager.flush();
        List<Map<String, Object>> before = snapshot();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get(URL)).andExpect(status().isOk());
            mockMvc.perform(get(URL + "/summary")).andExpect(status().isOk());
            mockMvc.perform(get(URL + "/" + id)).andExpect(status().isOk());
            mockMvc.perform(get(URL + "/" + id + "/payments")).andExpect(status().isOk());
        }
        entityManager.flush();
        assertThat(snapshot()).isEqualTo(before);
    }

    private String projectExpense(long projectId, long categoryId, String amount) {
        return """
                {"date":"2026-10-20","scope":"PROJECT","projectId":%d,"categoryId":%d,"description":"Excavator hire",
                 "payee":"Rajarata Hire","amount":%s""".formatted(projectId, categoryId, amount);
    }

    private String companyExpense(String amount) {
        return """
                {"date":"2026-10-20","scope":"COMPANY","categoryId":%d,"description":"Banner for the expo",
                 "payee":"Print Shop","amount":%s""".formatted(marketing, amount);
    }

    private String payment(UUID requestId, String amount) {
        return """
                {"requestId":"%s","paymentDate":"2026-10-21","amount":%s,"paymentMethodId":%d}""".formatted(requestId, amount, cash);
    }

    private ResultActions create(String body) throws Exception {
        return mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions update(long id, String body) throws Exception {
        return mockMvc.perform(put(URL + "/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions pay(long id, UUID requestId, String amount) throws Exception {
        return mockMvc.perform(post(URL + "/" + id + "/payments").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(payment(requestId, amount)));
    }

    private void expect(long id, String status, double paid, double outstanding) throws Exception {
        mockMvc.perform(get(URL + "/" + id))
                .andExpect(jsonPath("$.expense.status").value(status))
                .andExpect(jsonPath("$.expense.paid").value(paid))
                .andExpect(jsonPath("$.expense.outstanding").value(outstanding));
    }

    private long project(long clientId, String type, String name) throws Exception {
        String json = mockMvc.perform(post("/api/v1/projects").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"%s","type":"%s","clientId":%d,"contractValue":100000,"startDate":"2026-01-01",
                         "expectedCompletion":"2026-12-31","schedule":[{"description":"All","dueDate":"2026-12-31","amount":100000}]}"""
                        .formatted(name, type, clientId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private long category(String group, String name) {
        return jdbc.queryForObject("SELECT id FROM expense_categories WHERE expense_group = ? AND name = ?", Long.class, group, name);
    }

    private List<String> activity(long projectId) {
        return jdbc.queryForList("SELECT message FROM activity_log WHERE project_id = ? AND kind = 'EXPENSE' ORDER BY id",
                String.class, projectId);
    }

    private List<Map<String, Object>> snapshot() {
        return List.of(
                Map.of("expenses", jdbc.queryForList("SELECT * FROM expenses ORDER BY id")),
                Map.of("payments", jdbc.queryForList("SELECT * FROM expense_payments ORDER BY id")),
                Map.of("activity", jdbc.queryForObject("SELECT count(*) FROM activity_log", Long.class)));
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
