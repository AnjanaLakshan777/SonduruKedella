package com.sondurukedella.integration.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// The supplier directory and its link to expenses, against the real schema (V1 + V2). Each test rolls back.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser
class SupplierApiIntegrationTest {

    private static final String URL = "/api/v1/suppliers";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private long marketing;
    private long cash;

    @BeforeEach
    void setUp() {
        marketing = jdbc.queryForObject("SELECT id FROM expense_categories WHERE expense_group = 'COMPANY' AND name = 'Marketing'", Long.class);
        cash = jdbc.queryForObject("SELECT id FROM payment_methods WHERE name = 'Cash'", Long.class);
    }

    @Test
    void aSupplierIsCreatedTrimmedWithTheCompanyCreditDefault() throws Exception {
        int companyDefault = jdbc.queryForObject("SELECT supplier_credit_days FROM company_profile WHERE id = 1", Integer.class);

        long id = id(create("""
                {"name":"  Mihintale Hardware ","phone":" 025 222 3344 ","contactPerson":"  ","email":"  ",
                 "address":" Main Street, Mihintale ","supplies":"Cement, steel"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Mihintale Hardware"))
                .andExpect(jsonPath("$.phone").value("025 222 3344"))
                .andExpect(jsonPath("$.contactPerson").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.address").value("Main Street, Mihintale"))
                .andExpect(jsonPath("$.creditDays").value(companyDefault))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0)));

        // blanks really are stored as null, not ""
        Map<String, Object> row = jdbc.queryForMap("SELECT contact_person, email, active FROM suppliers WHERE id = ?", id);
        assertThat(row.get("contact_person")).isNull();
        assertThat(row.get("email")).isNull();
        assertThat(row.get("active")).isEqualTo(true);
        mockMvc.perform(get(URL + "/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.supplies").value("Cement, steel"));
    }

    @Test
    void invalidSuppliersAreRejectedAndNothingIsSaved() throws Exception {
        long before = jdbc.queryForObject("SELECT count(*) FROM suppliers", Long.class);
        create("{\"name\":\" \",\"phone\":\"0710000000\"}").andExpect(status().isBadRequest());
        create("{\"name\":\"X\"}").andExpect(status().isBadRequest());
        create("{\"name\":\"X\",\"phone\":\"0710000000\",\"email\":\"x@y\"}").andExpect(status().isBadRequest());
        create("{\"name\":\"X\",\"phone\":\"0710000000\",\"creditDays\":-3}").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM suppliers", Long.class)).isEqualTo(before);
    }

    @Test
    void anUnknownSupplierIs404() throws Exception {
        mockMvc.perform(get(URL + "/" + Long.MAX_VALUE)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Supplier not found."));
        mockMvc.perform(put(URL + "/" + Long.MAX_VALUE).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(update("X", 14, 0))).andExpect(status().isNotFound());
        setActive(Long.MAX_VALUE, false, 0).andExpect(status().isNotFound());
    }

    @Test
    void detailsAreUpdatedWithOptimisticLocking() throws Exception {
        long id = supplier("Rajarata Timber");

        mockMvc.perform(put(URL + "/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(update("Rajarata Timber Mills", 30, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rajarata Timber Mills"))
                .andExpect(jsonPath("$.creditDays").value(30))
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(put(URL + "/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(update("Stale", 30, 0)))
                .andExpect(status().isConflict());
        mockMvc.perform(get(URL + "/" + id)).andExpect(jsonPath("$.name").value("Rajarata Timber Mills"));
    }

    @Test
    void searchFiltersSortsAndPages() throws Exception {
        long sand = supplier("Zz Sand Depot");
        supplier("Zz Brick Yard");
        long cement = id(create("{\"name\":\"Zz Cement Co\",\"phone\":\"0719998877\",\"contactPerson\":\"Nimal Perera\"}"));
        setActive(sand, false, 0).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get(URL).param("q", "zz").param("size", "2"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[*].name").value(Matchers.contains("Zz Brick Yard", "Zz Cement Co")));
        mockMvc.perform(get(URL).param("q", "zz").param("active", "true")).andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(URL).param("q", "zz").param("active", "false"))
                .andExpect(jsonPath("$.content[*].id").value(Matchers.contains((int) sand)));
        mockMvc.perform(get(URL).param("q", "nimal")).andExpect(jsonPath("$.content[0].id").value(cement));  // contact person
        mockMvc.perform(get(URL).param("q", "999887")).andExpect(jsonPath("$.content[0].id").value(cement)); // phone
        mockMvc.perform(get(URL).param("q", "zz").param("sort", "name,desc"))
                .andExpect(jsonPath("$.content[0].name").value("Zz Sand Depot"));
        mockMvc.perform(get(URL).param("sort", "phone")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("size", "101")).andExpect(status().isBadRequest());
    }

    @Test
    void deactivatingAndReactivatingOnlyFlipsTheFlag() throws Exception {
        long id = supplier("Flip Supplies");
        setActive(id, false, 0).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.version").value(1));
        setActive(id, false, 0).andExpect(status().isConflict()); // stale
        setActive(id, true, 1).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        assertThat(jdbc.queryForObject("SELECT name FROM suppliers WHERE id = ?", String.class, id)).isEqualTo("Flip Supplies");
    }

    @Test
    void anExpenseCanNameASupplierAndBeFoundByIt() throws Exception {
        long supplier = supplier("Linked Hardware");

        long expense = id(expense(supplier, "Linked Hardware").andExpect(status().isCreated())
                .andExpect(jsonPath("$.supplierId").value(supplier))
                .andExpect(jsonPath("$.supplier").value("Linked Hardware")));
        expense(null, "Corner shop").andExpect(status().isCreated()).andExpect(jsonPath("$.supplierId").doesNotExist());

        mockMvc.perform(get("/api/v1/expenses").param("supplierId", String.valueOf(supplier)))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(expense));
        mockMvc.perform(get("/api/v1/expenses/summary").param("supplierId", String.valueOf(supplier)))
                .andExpect(jsonPath("$.total").value(2500.00));
    }

    @Test
    void anUnknownOrInactiveSupplierCannotBeLinkedToANewExpense() throws Exception {
        expense(Long.MAX_VALUE, "Ghost").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The selected supplier does not exist."));
        long closed = supplier("Closed Down Traders");
        setActive(closed, false, 0);
        expense(closed, "Closed Down Traders").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("is inactive")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses", Long.class)).isZero();
    }

    @Test
    void anExpenseKeepsItsSupplierAfterTheSupplierIsDeactivated() throws Exception {
        long supplier = supplier("Old Friend Hardware");
        long expense = id(expense(supplier, "Old Friend Hardware"));
        long payment = id(mockMvc.perform(post("/api/v1/expenses/" + expense + "/payments").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"requestId":"%s","paymentDate":"2026-10-21","amount":1000,"paymentMethodId":%d}"""
                        .formatted(UUID.randomUUID(), cash))).andExpect(status().isCreated()));
        setActive(supplier, false, 0);
        entityManager.clear();

        // still readable, still named, and still editable without changing the supplier
        mockMvc.perform(get("/api/v1/expenses/" + expense))
                .andExpect(jsonPath("$.expense.supplierId").value(supplier))
                .andExpect(jsonPath("$.expense.supplier").value("Old Friend Hardware"))
                .andExpect(jsonPath("$.payments[0].id").value(payment));
        updateExpense(expense, supplier, "3000", 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(3000.00)).andExpect(jsonPath("$.supplierId").value(supplier));
        // payments are untouched and still protect against overpaying
        mockMvc.perform(post("/api/v1/expenses/" + expense + "/payments").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId":"%s","paymentDate":"2026-10-22","amount":2000.01,"paymentMethodId":%d}"""
                        .formatted(UUID.randomUUID(), cash))).andExpect(status().isBadRequest());
    }

    @Test
    void anEditCannotLinkAnUnknownOrNewlyInactiveSupplier() throws Exception {
        long expense = id(expense(null, "Corner shop"));
        long inactive = supplier("Gone Supplies");
        setActive(inactive, false, 0);

        updateExpense(expense, Long.MAX_VALUE, "2500", 0).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The selected supplier does not exist."));
        updateExpense(expense, inactive, "2500", 0).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("is inactive")));
        assertThat(jdbc.queryForObject("SELECT supplier_id FROM expenses WHERE id = ?", Long.class, expense)).isNull();

        // linking an active one, then clearing it again, both work
        long active = supplier("Fresh Supplies");
        updateExpense(expense, active, "2500", 0).andExpect(status().isOk()).andExpect(jsonPath("$.supplier").value("Fresh Supplies"));
        updateExpense(expense, null, "2500", 1).andExpect(status().isOk()).andExpect(jsonPath("$.supplierId").doesNotExist());
    }

    @Test
    void theDatabaseRefusesAnExpenseThatNamesAMissingSupplier() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO expenses (expense_date, scope, category_id, expense_group, description, payee, supplier_id, amount)
                VALUES (DATE '2026-10-20', 'COMPANY', ?, 'COMPANY', 'x', 'x', ?, 10)""", marketing, Long.MAX_VALUE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void newSupplierRowsDefaultToActiveInTheDatabase() {
        // what V2 gives rows inserted without the column, e.g. by other tools
        long id = jdbc.queryForObject("INSERT INTO suppliers (name, phone) VALUES ('Raw Insert', '0710000000') RETURNING id", Long.class);
        assertThat(jdbc.queryForObject("SELECT active FROM suppliers WHERE id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    @WithAnonymousUser
    void anonymousRequestsAreRejected() throws Exception {
        mockMvc.perform(get(URL).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
    }

    private long supplier(String name) throws Exception {
        return id(create("{\"name\":\"%s\",\"phone\":\"0710000000\"}".formatted(name)).andExpect(status().isCreated()));
    }

    private ResultActions create(String body) throws Exception {
        return mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions setActive(long id, boolean active, long version) throws Exception {
        return mockMvc.perform(put(URL + "/" + id + "/status").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":%s,\"version\":%d}".formatted(active, version)));
    }

    private static String update(String name, int creditDays, long version) {
        return "{\"name\":\"%s\",\"phone\":\"0710000000\",\"creditDays\":%d,\"version\":%d}".formatted(name, creditDays, version);
    }

    private ResultActions expense(Long supplierId, String payee) throws Exception {
        return mockMvc.perform(post("/api/v1/expenses").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                {"date":"2026-10-20","scope":"COMPANY","categoryId":%d,"description":"Banner","payee":"%s",
                 "supplierId":%s,"amount":2500}""".formatted(marketing, payee, supplierId)));
    }

    private ResultActions updateExpense(long id, Long supplierId, String amount, long version) throws Exception {
        return mockMvc.perform(put("/api/v1/expenses/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                {"date":"2026-10-20","scope":"COMPANY","categoryId":%d,"description":"Banner","payee":"Shop",
                 "supplierId":%s,"amount":%s,"version":%d}""".formatted(marketing, supplierId, amount, version)));
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
