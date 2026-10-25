package com.sondurukedella.integration.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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

// Supplier invoices and payables against the real schema, and how they reach stock and the reports.
// Rolls back.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMIN")
class SupplierInvoiceApiIntegrationTest {

    private static final String URL = "/api/v1/supplier-invoices";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private long supplier;
    private long cash;
    private long bag;
    private long cube;
    private long cement;
    private long sand;
    private long construction;

    @BeforeEach
    void setUp() throws Exception {
        cash = jdbc.queryForObject("SELECT id FROM payment_methods WHERE name = 'Cash'", Long.class);
        bag = jdbc.queryForObject("SELECT id FROM units WHERE name = 'bag'", Long.class);
        cube = jdbc.queryForObject("SELECT id FROM units WHERE name = 'cube'", Long.class);
        supplier = id(send(post("/api/v1/suppliers"), "{\"name\":\"Mihintale Hardware\",\"phone\":\"0252223344\",\"creditDays\":30}"));
        cement = id(send(post("/api/v1/inventory/materials"), "{\"sku\":\"SI-CEM\",\"name\":\"SI Cement\",\"category\":\"Building\",\"unitId\":%d}".formatted(bag)));
        sand = id(send(post("/api/v1/inventory/materials"), "{\"sku\":\"SI-SAND\",\"name\":\"SI Sand\",\"category\":\"Building\",\"unitId\":%d}".formatted(cube)));
        long client = jdbc.queryForObject("INSERT INTO clients (name, phone) VALUES ('SI Client', '0710000000') RETURNING id", Long.class);
        construction = ApiFixtures.project(mockMvc, client, "CONSTRUCTION", "SI House");
    }

    @Test
    void aStockInvoiceGoesIntoStockAndIsNotACost() throws Exception {
        double stockBefore = number(body("/api/v1/reports/inventory-valuation"), "$.stockValue");
        String json = create(stockInvoice("INV-100", "2026-10-20", null,
                line(cement, null, "40", bag, "1250.25") + "," + line(sand, null, "3.5", cube, "999.99")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoice.total").value(53509.97)) // 50,010.00 + 3,499.97 (3,499.965 rounded half-up)
                .andExpect(jsonPath("$.invoice.dueDate").value("2026-11-19")) // + the supplier's 30 credit days
                .andExpect(jsonPath("$.invoice.status").value("UNPAID"))
                .andExpect(jsonPath("$.invoice.supplierName").value("Mihintale Hardware"))
                .andExpect(jsonPath("$.lines[0].description").value("SI Cement"))
                .andExpect(jsonPath("$.lines[1].lineAmount").value(3499.97))
                .andReturn().getResponse().getContentAsString();
        long invoiceId = (long) number(json, "$.invoice.id");

        // each line is one FIFO receipt, linked to its line
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM stock_movements m JOIN supplier_invoice_lines l ON l.id = m.supplier_invoice_line_id
                 WHERE l.invoice_id = ? AND m.movement_type = 'RECEIVED' AND m.invoice_allocation = 'STOCK'""", Long.class, invoiceId)).isEqualTo(2);
        assertThat(number(body("/api/v1/reports/inventory-valuation"), "$.stockValue") - stockBefore).isEqualTo(53509.97);
        mockMvc.perform(get("/api/v1/inventory/materials/" + cement)).andExpect(jsonPath("$.quantityOnHand").value(40));
        // not a project cost
        mockMvc.perform(get("/api/v1/reports/projects/" + construction + "/financial"))
                .andExpect(jsonPath("$.costs.supplierInvoices").value(0)).andExpect(jsonPath("$.costs.total").value(0));
    }

    @Test
    void aProjectInvoiceIsThatProjectsMaterialCostAndNotStock() throws Exception {
        long movementsBefore = jdbc.queryForObject("SELECT count(*) FROM stock_movements", Long.class);
        create(projectInvoice("SITE-7", construction, line(null, "Ready-mix concrete", "10", cube, "5000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoice.allocation").value("PROJECT"))
                .andExpect(jsonPath("$.invoice.total").value(50000.00));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movements", Long.class)).isEqualTo(movementsBefore);

        mockMvc.perform(get("/api/v1/reports/projects/" + construction + "/financial"))
                .andExpect(jsonPath("$.costs.supplierInvoices").value(50000.00))
                .andExpect(jsonPath("$.costs.materials").value(0))
                .andExpect(jsonPath("$.costs.total").value(50000.00));
        mockMvc.perform(get("/api/v1/reports/projects/" + construction + "/cost-breakdown"))
                .andExpect(jsonPath("$.categories[2].category").value("SUPPLIER_INVOICES"))
                .andExpect(jsonPath("$.categories[2].lines[0].label").value("SITE-7 (Mihintale Hardware)"))
                .andExpect(jsonPath("$.total").value(50000.00));
    }

    @Test
    void invalidInvoicesAreRejectedAndNothingIsSaved() throws Exception {
        long before = jdbc.queryForObject("SELECT count(*) FROM supplier_invoices", Long.class);
        create(stockInvoice("X1", "2026-10-20", null, line(null, "Cement", "1", bag, "10"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("choose the material")));
        create(stockInvoice("X2", "2026-10-20", null, line(cement, null, "1", cube, "10"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("kept in bag")));
        create(stockInvoice("X3", "2026-10-20", "2026-10-19", line(cement, null, "1", bag, "10"))).andExpect(status().isBadRequest());
        create(stockInvoice("X4", "2026-10-20", null, "")).andExpect(status().isBadRequest());
        create(stockInvoice("X5", "2026-10-20", null, line(cement, null, "0", bag, "10"))).andExpect(status().isBadRequest());
        create(projectInvoice("X6", null, line(null, "Concrete", "1", cube, "10"))).andExpect(status().isBadRequest());
        long client = jdbc.queryForObject("SELECT client_id FROM projects WHERE id = ?", Long.class, construction);
        long design = ApiFixtures.project(mockMvc, client, "DESIGN", "SI Plan");
        create(projectInvoice("X7", design, line(null, "Prints", "1", cube, "10"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("construction")));
        create(projectInvoice("X8", construction, line(null, " ", "1", cube, "10"))).andExpect(status().isBadRequest());
        create("{\"supplierId\":%d,\"invoiceNo\":\"X9\",\"invoiceDate\":\"2026-10-20\",\"allocation\":\"STOCK\",\"projectId\":%d,\"lines\":[%s]}"
                .formatted(supplier, construction, line(cement, null, "1", bag, "10"))).andExpect(status().isBadRequest());
        long version = jdbc.queryForObject("SELECT version FROM suppliers WHERE id = ?", Long.class, supplier);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/suppliers/" + supplier + "/status")
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false,\"version\":" + version + "}"))
                .andExpect(status().isOk());
        create(stockInvoice("X10", "2026-10-20", null, line(cement, null, "1", bag, "10"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("inactive")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM supplier_invoices", Long.class)).isEqualTo(before);
    }

    @Test
    void theSameInvoiceNumberFromTheSameSupplierIsRecordedOnce() throws Exception {
        create(projectInvoice("DUP-1", construction, line(null, "Concrete", "1", cube, "100"))).andExpect(status().isCreated());
        create(projectInvoice("DUP-1", construction, line(null, "Concrete", "1", cube, "100"))).andExpect(status().isConflict());
        long other = id(send(post("/api/v1/suppliers"), "{\"name\":\"Other Shop\",\"phone\":\"0250000000\"}"));
        create("{\"supplierId\":%d,\"invoiceNo\":\"DUP-1\",\"invoiceDate\":\"2026-10-20\",\"allocation\":\"PROJECT\",\"projectId\":%d,\"lines\":[%s]}"
                .formatted(other, construction, line(null, "Concrete", "1", cube, "100"))).andExpect(status().isCreated());
    }

    @Test
    void paymentsSettleThePayableOnceAndNeverOverpay() throws Exception {
        String payablesBefore = body("/api/v1/reports/dashboard");
        long id = invoiceId(create(projectInvoice("PAY-1", construction, line(null, "Steel", "2", cube, "30000"))));
        pay(id, UUID.randomUUID(), "20000").andExpect(status().isCreated()).andExpect(jsonPath("$.method").value("Cash"));
        mockMvc.perform(get(URL + "/" + id)).andExpect(jsonPath("$.invoice.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.invoice.outstanding").value(40000.00));
        pay(id, UUID.randomUUID(), "40000.01").andExpect(status().isBadRequest());

        UUID request = UUID.randomUUID();
        long first = (long) number(pay(id, request, "40000").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        assertThat((long) number(pay(id, request, "40000").andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id"))
                .isEqualTo(first);
        pay(id, request, "1").andExpect(status().isConflict());
        mockMvc.perform(get(URL + "/" + id)).andExpect(jsonPath("$.invoice.status").value("PAID"))
                .andExpect(jsonPath("$.payments.length()").value(2));

        // a void puts the amount back on the payable; the payment row stays
        mockMvc.perform(post(URL + "/" + id + "/payments/" + first + "/void").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Wrong invoice\",\"version\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.voided").value(true));
        mockMvc.perform(post(URL + "/" + id + "/payments/" + first + "/void").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"again\",\"version\":1}")).andExpect(status().isConflict());
        mockMvc.perform(get(URL + "/" + id)).andExpect(jsonPath("$.invoice.outstanding").value(40000.00));

        // payments never change the project's cost
        mockMvc.perform(get("/api/v1/reports/projects/" + construction + "/financial")).andExpect(jsonPath("$.costs.supplierInvoices").value(60000.00));
        String payablesAfter = body("/api/v1/reports/dashboard");
        assertThat(number(payablesAfter, "$.payables.suppliers") - number(payablesBefore, "$.payables.suppliers")).isEqualTo(40000.0);
        pay(999_999_999L, UUID.randomUUID(), "1").andExpect(status().isNotFound());
    }

    @Test
    void anInitialPaymentIsRecordedWithTheInvoiceAndOverdueIsShown() throws Exception {
        String json = create("""
                {"supplierId":%d,"invoiceNo":"OLD-1","invoiceDate":"2026-09-01","dueDate":"2026-09-10","allocation":"PROJECT","projectId":%d,
                 "lines":[%s],"initialPayment":{"requestId":"%s","paymentDate":"2026-09-01","amount":400,"paymentMethodId":%d}}"""
                .formatted(supplier, construction, line(null, "Bolts", "1", cube, "1000"), UUID.randomUUID(), cash))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoice.paid").value(400.00))
                .andExpect(jsonPath("$.invoice.status").value("OVERDUE"))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(get(URL).param("supplierId", String.valueOf(supplier)).param("status", "OVERDUE"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].invoiceNo").value("OLD-1"));
        mockMvc.perform(get(URL + "/summary").param("supplierId", String.valueOf(supplier)))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.projectTotal").value(1000.00))
                .andExpect(jsonPath("$.stockTotal").value(0))
                .andExpect(jsonPath("$.outstanding").value(600.00))
                .andExpect(jsonPath("$.overdue").value(600.00));
        assertThat(number(json, "$.payments.length()")).isEqualTo(1);
        mockMvc.perform(get(URL).param("sort", "total")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("from", "2026-10-02").param("to", "2026-10-01")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL + "/999999999")).andExpect(status().isNotFound());
    }

    @Test
    void theFinancialSummarySeparatesStockPurchasesFromProjectCost() throws Exception {
        String range = "/api/v1/reports/financial-summary?from=2026-10-01&to=2026-10-31";
        String before = body(range);
        create(stockInvoice("FS-1", "2026-10-20", null, line(cement, null, "10", bag, "1000"))).andExpect(status().isCreated());
        create(projectInvoice("FS-2", construction, line(null, "Concrete", "1", cube, "2500"))).andExpect(status().isCreated());
        String after = body(range);
        assertThat(number(after, "$.supplierInvoices.stockPurchases") - number(before, "$.supplierInvoices.stockPurchases")).isEqualTo(10000.0);
        assertThat(number(after, "$.supplierInvoices.projectInvoices") - number(before, "$.supplierInvoices.projectInvoices")).isEqualTo(2500.0);
        assertThat(number(after, "$.position.suppliersOutstanding") - number(before, "$.position.suppliersOutstanding")).isEqualTo(12500.0);
        // buying into stock is not material use
        assertThat(number(after, "$.materials.net") - number(before, "$.materials.net")).isZero();
    }

    private String stockInvoice(String no, String date, String due, String lines) {
        return "{\"supplierId\":%d,\"invoiceNo\":\"%s\",\"invoiceDate\":\"%s\"%s,\"allocation\":\"STOCK\",\"lines\":[%s]}"
                .formatted(supplier, no, date, due == null ? "" : ",\"dueDate\":\"" + due + "\"", lines);
    }

    private String projectInvoice(String no, Long projectId, String lines) {
        return "{\"supplierId\":%d,\"invoiceNo\":\"%s\",\"invoiceDate\":\"2026-10-20\",\"allocation\":\"PROJECT\"%s,\"lines\":[%s]}"
                .formatted(supplier, no, projectId == null ? "" : ",\"projectId\":" + projectId, lines);
    }

    private static String line(Long material, String description, String quantity, long unit, String price) {
        return "{" + (material == null ? "" : "\"materialId\":" + material + ",") + (description == null ? "" : "\"description\":\"" + description + "\",")
                + "\"quantity\":" + quantity + ",\"unitId\":" + unit + ",\"unitPrice\":" + price + "}";
    }

    private ResultActions create(String json) throws Exception {
        return mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions pay(long id, UUID requestId, String amount) throws Exception {
        return mockMvc.perform(post(URL + "/" + id + "/payments").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"%s\",\"paymentDate\":\"2026-10-21\",\"amount\":%s,\"paymentMethodId\":%d}".formatted(requestId, amount, cash)));
    }

    private String send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private String body(String url) throws Exception {
        return mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static long invoiceId(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.invoice.id")).longValue();
    }

    private static long id(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private static double number(String json, String path) {
        return ((Number) JsonPath.read(json, path)).doubleValue();
    }

}
