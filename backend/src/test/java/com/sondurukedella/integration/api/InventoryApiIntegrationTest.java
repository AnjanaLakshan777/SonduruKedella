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
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// Materials, the stock ledger and FIFO costing against the real schema (V1-V4). Each test rolls back.
// Concurrent postings and real-commit rollback are in InventoryConcurrencyIntegrationTest.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser
class InventoryApiIntegrationTest {

    private static final String URL = "/api/v1/inventory";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private long bag;
    private long cube;
    private long house;     // construction, planning
    private long site;      // another construction project
    private long cement;

    @BeforeEach
    void setUp() throws Exception {
        bag = jdbc.queryForObject("SELECT id FROM units WHERE name = 'bag'", Long.class);
        cube = jdbc.queryForObject("SELECT id FROM units WHERE name = 'cube'", Long.class);
        long client = jdbc.queryForObject("INSERT INTO clients (name, phone) VALUES ('Stock Client', '0710000000') RETURNING id", Long.class);
        house = project(client, "CONSTRUCTION", "House");
        site = project(client, "CONSTRUCTION", "Site");
        cement = id(material("CEM-42", "Cement 42.5N", bag).andExpect(status().isCreated()));
    }

    @Test
    void aMaterialIsCreatedWithNoStockAndItsSkuIsUnique() throws Exception {
        mockMvc.perform(get(URL + "/materials/" + cement))
                .andExpect(jsonPath("$.sku").value("CEM-42"))
                .andExpect(jsonPath("$.unit").value("bag"))
                .andExpect(jsonPath("$.quantityOnHand").value(0))
                .andExpect(jsonPath("$.stockValue").value(0))
                .andExpect(jsonPath("$.defaultUnitCost").value(0))
                .andExpect(jsonPath("$.active").value(true));
        material("cem-42", "Duplicate", bag).andExpect(status().isConflict()); // case-insensitive, as uq_materials_sku
        material("NEW-1", "Bad unit", Long.MAX_VALUE).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Choose a unit from the Settings list."));
        mockMvc.perform(get(URL + "/materials/" + Long.MAX_VALUE)).andExpect(status().isNotFound());
        mockMvc.perform(get(URL + "/materials/" + Long.MAX_VALUE + "/layers")).andExpect(status().isNotFound());
    }

    @Test
    void materialsCanBeSearchedFilteredSortedAndPaged() throws Exception {
        id(material("ZZ-SAND", "Zz River Sand", cube));
        long brick = id(material("ZZ-BRICK", "Zz Brick", bag));
        setActive(brick, false, 0).andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get(URL + "/materials").param("q", "zz").param("size", "1"))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Zz Brick"));
        mockMvc.perform(get(URL + "/materials").param("q", "zz").param("active", "true"))
                .andExpect(jsonPath("$.content[*].name").value(Matchers.contains("Zz River Sand")));
        mockMvc.perform(get(URL + "/materials").param("category", "BUILDING")).andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get(URL + "/materials").param("q", "zz").param("sort", "sku,desc"))
                .andExpect(jsonPath("$.content[0].sku").value("ZZ-SAND"));
        mockMvc.perform(get(URL + "/materials").param("sort", "unitCost")).andExpect(status().isBadRequest());
    }

    @Test
    void everyReceiptIsItsOwnLayerAndTheStockIsWorthTheirSum() throws Exception {
        long first = id(receive(cement, "100", "2500").andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCost").value(250000.00)).andExpect(jsonPath("$.quantityAfter").value(100))
                .andExpect(jsonPath("$.costingMethod").value("FIFO")).andExpect(jsonPath("$.allocations.length()").value(0)));
        receive(cement, "50", "2800").andExpect(status().isCreated()).andExpect(jsonPath("$.valueAfter").value(390000.00));
        receive(cement, "30", "2710");
        expectStock(cement, "180", "471300.00"); // 250,000 + 140,000 + 81,300 - nothing is averaged

        mockMvc.perform(get(URL + "/materials/" + cement + "/layers"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].origin").value("RECEIPT"))
                .andExpect(jsonPath("$[0].sourceMovementId").value(first))
                .andExpect(jsonPath("$[*].unitCost").value(Matchers.contains(2500.0, 2800.0, 2710.0)))
                .andExpect(jsonPath("$[*].remainingValue").value(Matchers.contains(250000.0, 140000.0, 81300.0)));
        mockMvc.perform(get(URL + "/movements/" + first)).andExpect(jsonPath("$.costLayerId").isNumber());
        mockMvc.perform(get(URL + "/valuation"))
                .andExpect(jsonPath("$.stockValue").value(471300.00)).andExpect(jsonPath("$.materialsInStock").value(1));
        assertLayersMatchTheLedger(cement);
    }

    @Test
    void theOwnersExampleChargesTheOldestStockFirst() throws Exception {
        receive(cement, "10", "2000");
        receive(cement, "10", "2400");

        long issue = id(issue(cement, house, "12").andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("ISSUED"))
                .andExpect(jsonPath("$.quantityChange").value(-12))
                .andExpect(jsonPath("$.totalCost").value(-24800.00))      // 10 x 2,000 + 2 x 2,400
                .andExpect(jsonPath("$.unitCost").value(2066.6667))       // display only: 24,800 / 12
                .andExpect(jsonPath("$.allocations.length()").value(2))
                .andExpect(jsonPath("$.allocations[0].quantity").value(-10))
                .andExpect(jsonPath("$.allocations[0].unitCost").value(2000.0))
                .andExpect(jsonPath("$.allocations[0].cost").value(-20000.00))
                .andExpect(jsonPath("$.allocations[1].quantity").value(-2))
                .andExpect(jsonPath("$.allocations[1].unitCost").value(2400.0))
                .andExpect(jsonPath("$.allocations[1].cost").value(-4800.00))
                .andExpect(jsonPath("$.returnableQuantity").value(12)));
        // what's left is 8 of the second receipt, worth 19,200
        expectStock(cement, "8", "19200.00");
        mockMvc.perform(get(URL + "/materials/" + cement + "/layers"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].remainingQuantity").value(8)).andExpect(jsonPath("$[0].remainingValue").value(19200.00));
        mockMvc.perform(get(URL + "/materials/" + cement + "/layers").param("openOnly", "false"))
                .andExpect(jsonPath("$[0].remainingQuantity").value(0)).andExpect(jsonPath("$[0].remainingValue").value(0));

        // a dearer purchase later changes nothing about what the house was charged
        receive(cement, "10", "4000");
        mockMvc.perform(get(URL + "/movements/" + issue)).andExpect(jsonPath("$.totalCost").value(-24800.00));
        usage(house).andExpect(jsonPath("$.netCost").value(24800.00))
                .andExpect(jsonPath("$.materials[0].issuedQuantity").value(12));
        assertThat(activity(house)).containsExactly("Materials issued: 12 bag Cement 42.5N (LKR 24,800.00).");
        assertLayersMatchTheLedger(cement);
    }

    @Test
    void successiveIssuesWalkThroughTheLayersInOrder() throws Exception {
        receive(cement, "10", "100");
        receive(cement, "10", "200");
        receive(cement, "10", "300");

        issue(cement, house, "5").andExpect(jsonPath("$.totalCost").value(-500.00));     // 5 of the first
        expectStock(cement, "25", "5500.00");
        issue(cement, house, "10").andExpect(jsonPath("$.totalCost").value(-1500.00));   // last 5 of the first + 5 of the second
        expectStock(cement, "15", "4000.00");
        issue(cement, site, "10").andExpect(jsonPath("$.totalCost").value(-2500.00))     // last 5 of the second + 5 of the third
                .andExpect(jsonPath("$.allocations[*].cost").value(Matchers.contains(-1000.0, -1500.0)));
        expectStock(cement, "5", "1500.00");
        assertLayersMatchTheLedger(cement);
    }

    @Test
    void eachMaterialHasItsOwnLayers() throws Exception {
        long sand = id(material("SND-1", "Sand", cube));
        receive(cement, "10", "2000");
        receive(sand, "5", "18000");
        receive(cement, "10", "2400");

        issue(sand, house, "2").andExpect(jsonPath("$.totalCost").value(-36000.00));
        issue(cement, house, "11").andExpect(jsonPath("$.totalCost").value(-22400.00)); // never touched by sand's layer
        expectStock(sand, "3", "54000.00");
        expectStock(cement, "9", "21600.00");
        assertLayersMatchTheLedger(cement);
        assertLayersMatchTheLedger(sand);
    }

    @Test
    void issuesCannotTakeMoreThanIsInStock() throws Exception {
        receive(cement, "10", "2500");
        issue(cement, house, "10.001").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Only 10 bag of Cement 42.5N in stock."));
        issue(cement, house, "10").andExpect(status().isCreated()).andExpect(jsonPath("$.valueAfter").value(0));
        issue(cement, house, "0.001").andExpect(status().isBadRequest());
        expectStock(cement, "0", "0.00");
        // the refused issues left nothing behind
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movements WHERE material_id = ? AND movement_type = 'ISSUED'",
                Long.class, cement)).isOne();
    }

    @Test
    void afterRunningOutTheNextReceiptPricesTheNextIssue() throws Exception {
        receive(cement, "10", "2500");
        issue(cement, house, "10");
        receive(cement, "20", "3000");
        issue(cement, house, "5").andExpect(jsonPath("$.totalCost").value(-15000.00));
        expectStock(cement, "15", "45000.00");
    }

    @Test
    void issuesGoOnlyToOpenConstructionProjects() throws Exception {
        receive(cement, "100", "2500");
        long client = jdbc.queryForObject("SELECT client_id FROM projects WHERE id = ?", Long.class, house);
        long design = project(client, "DESIGN", "Plan");
        issue(cement, design, "1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Materials can only be issued to construction projects."));
        issue(cement, Long.MAX_VALUE, "1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The selected project does not exist."));
        for (String closed : List.of("COMPLETED", "CANCELLED")) {
            long p = project(client, "CONSTRUCTION", closed);
            mockMvc.perform(put("/api/v1/projects/" + p + "/status").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"" + closed + "\",\"version\":0}")).andExpect(status().isOk());
            issue(cement, p, "1").andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("can't take new material issues")));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_cost_allocations WHERE material_id = ?", Long.class, cement)).isZero();
    }

    @Test
    void partialReturnsGoBackIntoTheLayersTheIssueCameFrom() throws Exception {
        long first = layerOf(id(receive(cement, "10", "2000")));
        long second = layerOf(id(receive(cement, "10", "2400")));
        long issue = id(issue(cement, house, "12"));      // 10 from the first layer, 2 from the second: 24,800
        receive(cement, "10", "4000");                    // returns never come back at this price

        // newest draw first: 1 from the second layer's draw
        giveBack(issue, "1").andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("RETURNED"))
                .andExpect(jsonPath("$.totalCost").value(2400.00))
                .andExpect(jsonPath("$.returnedFromMovementId").value(issue))
                .andExpect(jsonPath("$.allocations.length()").value(1))
                .andExpect(jsonPath("$.allocations[0].layerId").value(second));
        // the second draw's last unit, then into the first layer's draw: 2,400 + 2,000
        giveBack(issue, "2").andExpect(jsonPath("$.totalCost").value(4400.00))
                .andExpect(jsonPath("$.allocations[*].layerId").value(Matchers.contains((int) second, (int) first)));
        expectLayer(first, "1", "2000.00");
        expectLayer(second, "10", "24000.00");
        mockMvc.perform(get(URL + "/movements/" + issue))
                .andExpect(jsonPath("$.returnedQuantity").value(3)).andExpect(jsonPath("$.returnableQuantity").value(9));

        giveBack(issue, "9.001").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Only 9 bag of this issue can still be returned."));
        giveBack(issue, "9").andExpect(status().isCreated()).andExpect(jsonPath("$.totalCost").value(18000.00));
        giveBack(issue, "0.001").andExpect(status().isBadRequest());

        // fully returned: both layers are back to exactly what they held, and the house is charged nothing
        expectLayer(first, "10", "20000.00");
        expectLayer(second, "10", "24000.00");
        usage(house).andExpect(jsonPath("$.issuedCost").value(24800.00))
                .andExpect(jsonPath("$.returnedCost").value(24800.00))
                .andExpect(jsonPath("$.netCost").value(0))
                .andExpect(jsonPath("$.materials[0].netQuantity").value(0));
        assertThat(activity(house)).hasSize(4).last().asString()
                .isEqualTo("Materials returned to stock: 9 bag Cement 42.5N (LKR 18,000.00 off the project's costs).");
        assertLayersMatchTheLedger(cement);
    }

    @Test
    void aFullReturnInOneGoReversesEveryDraw() throws Exception {
        long single = id(material("SND-1", "Sand", cube));
        long sandLayer = layerOf(id(receive(single, "5", "18000")));
        long sandIssue = id(issue(single, site, "3"));
        giveBack(sandIssue, "3").andExpect(jsonPath("$.totalCost").value(54000.00));
        expectLayer(sandLayer, "5", "90000.00");

        long first = layerOf(id(receive(cement, "10", "2000")));
        long second = layerOf(id(receive(cement, "10", "2400")));
        long issue = id(issue(cement, house, "12"));
        giveBack(issue, "12").andExpect(jsonPath("$.totalCost").value(24800.00))
                .andExpect(jsonPath("$.allocations.length()").value(2));
        expectLayer(first, "10", "20000.00");
        expectLayer(second, "10", "24000.00");
        usage(house).andExpect(jsonPath("$.netCost").value(0));
    }

    @Test
    void returnsReconcileToTheCentWhenCostsDoNotDivideEvenly() throws Exception {
        long first = layerOf(id(receive(cement, "3", "33.3333")));     // 99.9999 -> 100.00
        receive(cement, "1.5", "10.0001");                            // 15.00015 -> 15.00
        long issue = id(issue(cement, house, "4").andExpect(jsonPath("$.totalCost").value(-110.00))); // 100.00 + 10.00

        BigDecimal back = BigDecimal.ZERO;
        for (String expected : List.of("10.00", "33.33", "33.33", "33.34")) {
            String json = giveBack(issue, "1").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            BigDecimal cost = new BigDecimal(JsonPath.read(json, "$.totalCost").toString());
            assertThat(cost).isEqualByComparingTo(expected);
            back = back.add(cost);
        }
        assertThat(back).isEqualByComparingTo("110.00");
        expectLayer(first, "3", "100.00");
        usage(house).andExpect(jsonPath("$.netCost").value(0));
        assertLayersMatchTheLedger(cement);
    }

    @Test
    void aReturnMustMatchItsIssue() throws Exception {
        receive(cement, "100", "2500");
        long receipt = jdbc.queryForObject("SELECT max(id) FROM stock_movements WHERE material_id = ?", Long.class, cement);
        long issue = id(issue(cement, house, "10"));
        long sand = id(material("SND-1", "Sand", cube));

        returnBody("{\"issueId\":%d,\"materialId\":%d".formatted(issue, sand)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("That issue was for a different material."));
        returnBody("{\"issueId\":%d,\"projectId\":%d".formatted(issue, site)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("That issue was to a different project."));
        returnBody("{\"issueId\":%d".formatted(receipt)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The selected issue does not exist."));
        returnBody("{\"issueId\":%d".formatted(Long.MAX_VALUE)).andExpect(status().isBadRequest());
        returnBody("{\"issueId\":%d,\"materialId\":%d,\"projectId\":%d".formatted(issue, cement, house)).andExpect(status().isCreated());
    }

    @Test
    void reductionsDrawFifoAndAdditionsNeedAStatedCost() throws Exception {
        receive(cement, "10", "2000");
        receive(cement, "10", "2400");
        adjust(cement, "-12", "12 bags hardened in the rain", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCost").value(-24800.00)).andExpect(jsonPath("$.allocations.length()").value(2))
                .andExpect(jsonPath("$.notes").value("12 bags hardened in the rain"));

        adjust(cement, "2", "Count found 2 more", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Enter the unit cost")));
        long found = layerOf(id(adjust(cement, "2", "Count found 2 more", "2500").andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCost").value(5000.00)).andExpect(jsonPath("$.unitCost").value(2500.0))));
        mockMvc.perform(get(URL + "/materials/" + cement + "/layers"))
                .andExpect(jsonPath("$[*].origin").value(Matchers.contains("RECEIPT", "ADJUSTMENT")));
        expectStock(cement, "10", "24200.00");

        adjust(cement, "-1", "Damaged in store", "2400").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("leave the unit cost empty")));
        adjust(cement, "-1000", "Way too many", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Only 10 bag of Cement 42.5N in stock."));
        adjust(cement, "0", "Nothing at all", "1").andExpect(status().isBadRequest());
        adjust(cement, "1", "fix", "1").andExpect(status().isBadRequest());

        // 8 left of the 2,400 layer, then 1 of the found stock
        adjust(cement, "-9", "Stock count difference", null).andExpect(jsonPath("$.totalCost").value(-21700.00));
        expectLayer(found, "1", "2500.00");
        expectStock(cement, "1", "2500.00");
        // adjustments never touch a project
        usage(house).andExpect(jsonPath("$.materials.length()").value(0));
        assertLayersMatchTheLedger(cement);
    }

    @Test
    void inactiveMaterialsTakeNoNewStockButKeepTheirHistoryAndReturns() throws Exception {
        receive(cement, "100", "2500");
        long issue = id(issue(cement, house, "10"));
        setActive(cement, false, jdbc.queryForObject("SELECT version FROM materials WHERE id = ?", Long.class, cement))
                .andExpect(status().isOk());

        receive(cement, "1", "2500").andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("is inactive")));
        issue(cement, house, "1").andExpect(status().isBadRequest());
        adjust(cement, "-1", "Damaged in store", null).andExpect(status().isBadRequest());
        giveBack(issue, "5").andExpect(status().isCreated()); // stock already on site can always come back
        mockMvc.perform(get(URL + "/materials/" + cement + "/movements")).andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get(URL + "/materials/" + cement)).andExpect(jsonPath("$.quantityOnHand").value(95));
    }

    @Test
    void receiptsMayNameAnActiveSupplier() throws Exception {
        long supplier = supplier("Mihintale Hardware");
        receiveFrom(cement, supplier).andExpect(status().isCreated())
                .andExpect(jsonPath("$.supplierId").value(supplier)).andExpect(jsonPath("$.supplier").value("Mihintale Hardware"));
        receiveFrom(cement, Long.MAX_VALUE).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The selected supplier does not exist."));

        mockMvc.perform(put("/api/v1/suppliers/" + supplier + "/status").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false,\"version\":0}")).andExpect(status().isOk());
        receiveFrom(cement, supplier).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("is inactive")));
        // the earlier receipt still names it
        mockMvc.perform(get(URL + "/movements").param("supplierId", String.valueOf(supplier)))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].supplier").value("Mihintale Hardware"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_cost_layers WHERE material_id = ?", Long.class, cement)).isOne();
    }

    @Test
    void aResentSubmissionIsPostedOnceWithItsAllocationsOnce() throws Exception {
        UUID requestId = UUID.randomUUID();
        long first = id(receiveWith(requestId, cement, "100", "2500").andExpect(status().isCreated()));
        receiveWith(requestId, cement, "100", "2500").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(first));
        receiveWith(requestId, cement, "99", "2500").andExpect(status().isConflict());
        expectStock(cement, "100", "250000.00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_cost_layers WHERE material_id = ?", Long.class, cement)).isOne();

        receive(cement, "10", "3000");
        UUID issueId = UUID.randomUUID();
        long issue = id(issueWith(issueId, cement, house, "105").andExpect(status().isCreated()));
        issueWith(issueId, cement, house, "105").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(issue))
                .andExpect(jsonPath("$.allocations.length()").value(2));
        issueWith(issueId, cement, site, "105").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_cost_allocations WHERE material_id = ?", Long.class, cement)).isEqualTo(2);
        expectStock(cement, "5", "15000.00");
    }

    @Test
    void movementHistoryFiltersByTypeProjectAndDate() throws Exception {
        receive(cement, "100", "2500");
        long issue = id(issue(cement, house, "10"));
        issue(cement, site, "5");
        giveBack(issue, "2");

        mockMvc.perform(get(URL + "/movements").param("materialId", String.valueOf(cement)))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].type").value("RETURNED")) // newest first
                .andExpect(jsonPath("$.content[0].allocations.length()").value(1));
        mockMvc.perform(get(URL + "/movements").param("type", "ISSUED")).andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(URL + "/movements").param("projectId", String.valueOf(house))).andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(URL + "/movements").param("from", "2026-10-21")).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(URL + "/movements").param("from", "2026-10-21").param("to", "2026-10-20")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL + "/movements/" + Long.MAX_VALUE)).andExpect(status().isNotFound());
        mockMvc.perform(get(URL + "/projects/" + Long.MAX_VALUE + "/usage")).andExpect(status().isNotFound());
    }

    @Test
    void theLedgerTheLayersAndTheAllocationsAlwaysAgree() throws Exception {
        receive(cement, "100", "2500");
        receive(cement, "33.333", "2777.7777");
        long issue = id(issue(cement, house, "107.777"));
        giveBack(issue, "12.345");
        adjust(cement, "-3.21", "Spillage on the yard", null);
        adjust(cement, "1.5", "Found under a tarp", "2600");
        issue(cement, site, "20");

        Map<String, Object> sums = jdbc.queryForMap(
                "SELECT sum(quantity_change) AS q, sum(total_cost) AS v FROM stock_movements WHERE material_id = ?", cement);
        Map<String, Object> last = jdbc.queryForMap(
                "SELECT quantity_after AS q, value_after AS v FROM stock_movements WHERE material_id = ? ORDER BY id DESC LIMIT 1", cement);
        assertThat((BigDecimal) last.get("q")).isEqualByComparingTo((BigDecimal) sums.get("q"));
        assertThat((BigDecimal) last.get("v")).isEqualByComparingTo((BigDecimal) sums.get("v"));
        assertLayersMatchTheLedger(cement);
    }

    @Test
    void returnsAgainstIssuesFromBeforeFifoComeBackAtTheirOwnCost() throws Exception {
        // what V4 finds in a database used under the weighted average: a receipt and an issue priced at the
        // average, and the balance carried forward as one LEGACY_BALANCE layer
        long receipt = legacyMovement("RECEIVED", "100", "2500", "250000.00", "100", "250000.00", null, null);
        long issue = legacyMovement("ISSUED", "-40", "2500", "-100000.00", "60", "150000.00", house, null);
        long carried = jdbc.queryForObject("""
                INSERT INTO stock_cost_layers (material_id, source_movement_id, origin, layer_date, original_quantity, unit_cost,
                                               original_cost, remaining_quantity, remaining_value)
                VALUES (?, ?, 'LEGACY_BALANCE', DATE '2026-10-01', 60, 2500, 150000, 60, 150000) RETURNING id""", Long.class, cement, issue);
        entityManager.clear();

        mockMvc.perform(get(URL + "/movements/" + issue)).andExpect(jsonPath("$.costingMethod").value("WAC"))
                .andExpect(jsonPath("$.allocations.length()").value(0)).andExpect(jsonPath("$.totalCost").value(-100000.00));
        mockMvc.perform(get(URL + "/movements/" + receipt)).andExpect(jsonPath("$.costLayerId").doesNotExist());
        long back = layerOf(id(giveBack(issue, "10").andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCost").value(25000.00)).andExpect(jsonPath("$.unitCost").value(2500.0))));
        receive(cement, "10", "3000");

        mockMvc.perform(get(URL + "/materials/" + cement + "/layers"))
                .andExpect(jsonPath("$[*].origin").value(Matchers.contains("LEGACY_BALANCE", "LEGACY_RETURN", "RECEIPT")));
        // the carried-forward stock is used first, then the returned stock, then the new receipt
        issue(cement, site, "75").andExpect(jsonPath("$.totalCost").value(-190000.00)); // 60 + 10 at 2,500, 5 at 3,000
        expectLayer(carried, "0", "0.00");
        expectLayer(back, "0", "0.00");
        expectStock(cement, "5", "15000.00");
        // the house's pre-FIFO charge is untouched; only the return came off it
        usage(house).andExpect(jsonPath("$.issuedCost").value(100000.00)).andExpect(jsonPath("$.netCost").value(75000.00));
        assertLayersMatchTheLedger(cement);
    }

    @Test
    void theUnitIsFixedOnceStockHasMovedButTheDefaultCostIsOnlyAReference() throws Exception {
        updateMaterial(cement, "CEM-42", cube, "100", 0).andExpect(status().isOk()); // no movements yet: allowed
        receive(cement, "10", "2500");
        updateMaterial(cement, "CEM-42", bag, null, 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("unit can't change")));
        // changing the reference price revalues nothing
        updateMaterial(cement, "CEM-42", cube, "9999", 1).andExpect(status().isOk()).andExpect(jsonPath("$.defaultUnitCost").value(9999.0))
                .andExpect(jsonPath("$.stockValue").value(25000.00));
        updateMaterial(cement, "CEM-42 B", cube, null, 2).andExpect(status().isOk()).andExpect(jsonPath("$.sku").value("CEM-42 B"));
        updateMaterial(cement, "CEM-42 B", cube, null, 2).andExpect(status().isConflict()); // stale
        issue(cement, house, "1").andExpect(jsonPath("$.totalCost").value(-2500.00));
    }

    @Test
    void theDatabaseGuardsTheLedgerLayersAndAllocations() throws Exception {
        receive(cement, "10", "2500");
        long issue = id(issue(cement, house, "4"));
        entityManager.flush();
        long id = jdbc.queryForObject("SELECT max(id) FROM stock_movements WHERE material_id = ?", Long.class, cement);
        assertRefused("UPDATE stock_movements SET reference = 'x' WHERE id = ?", id);
        Map<String, Object> draw = jdbc.queryForMap("SELECT id, layer_id FROM stock_cost_allocations WHERE movement_id = ?", issue);
        assertRefused("UPDATE stock_cost_allocations SET cost = 0 WHERE id = ?", draw.get("id"));
        assertRefused("DELETE FROM stock_cost_allocations WHERE id = ?", draw.get("id"));
        // a layer can't hold more than came in, nor be empty and still worth something
        assertRefused("UPDATE stock_cost_layers SET remaining_quantity = 11 WHERE id = ?", draw.get("layer_id"));
        assertRefused("UPDATE stock_cost_layers SET remaining_quantity = 0 WHERE id = ?", draw.get("layer_id"));
        // and a return can't put back more than its draw took (1 back already, 4 more would make 5 of 4)
        long ret = id(giveBack(issue, "1"));
        entityManager.flush();
        assertRefused("""
                INSERT INTO stock_cost_allocations (movement_id, material_id, layer_id, quantity, cost, reverses_allocation_id)
                VALUES (?, ?, ?, 4, 0, ?)""", ret, cement, draw.get("layer_id"), draw.get("id"));
    }

    // a refused statement aborts a PostgreSQL transaction, so each one gets its own savepoint
    private void assertRefused(String sql, Object... args) {
        jdbc.execute("SAVEPOINT guard");
        assertThatThrownBy(() -> jdbc.update(sql, args)).isInstanceOf(DataAccessException.class);
        jdbc.execute("ROLLBACK TO SAVEPOINT guard");
    }

    @Test
    void readingChangesNothing() throws Exception {
        receive(cement, "100", "2500");
        long issue = id(issue(cement, house, "10"));
        giveBack(issue, "1");
        entityManager.flush();
        List<Map<String, Object>> before = snapshot();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get(URL + "/materials")).andExpect(status().isOk());
            mockMvc.perform(get(URL + "/materials/" + cement)).andExpect(status().isOk());
            mockMvc.perform(get(URL + "/materials/" + cement + "/movements")).andExpect(status().isOk());
            mockMvc.perform(get(URL + "/materials/" + cement + "/layers")).andExpect(status().isOk());
            mockMvc.perform(get(URL + "/movements")).andExpect(status().isOk());
            mockMvc.perform(get(URL + "/valuation")).andExpect(status().isOk());
            usage(house).andExpect(status().isOk());
        }
        entityManager.flush();
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @WithAnonymousUser
    void anonymousRequestsAreRejected() throws Exception {
        mockMvc.perform(get(URL + "/materials").accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
    }

    private ResultActions material(String sku, String name, long unitId) throws Exception {
        // setup helpers sign in themselves, so they also work under @WithAnonymousUser
        return mockMvc.perform(post(URL + "/materials").with(user("setup")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                {"sku":"%s","name":"%s","category":"Building","unitId":%d}""".formatted(sku, name, unitId)));
    }

    private ResultActions updateMaterial(long id, String sku, long unitId, String cost, long version) throws Exception {
        String costField = cost == null ? "" : ",\"defaultUnitCost\":" + cost;
        return mockMvc.perform(put(URL + "/materials/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                {"sku":"%s","name":"Cement 42.5N","category":"Building","unitId":%d%s,"version":%d}"""
                .formatted(sku, unitId, costField, version)));
    }

    private ResultActions setActive(long id, boolean active, long version) throws Exception {
        return mockMvc.perform(put(URL + "/materials/" + id + "/status").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":%s,\"version\":%d}".formatted(active, version)));
    }

    private ResultActions receive(long materialId, String quantity, String unitCost) throws Exception {
        return receiveWith(UUID.randomUUID(), materialId, quantity, unitCost);
    }

    private ResultActions receiveWith(UUID requestId, long materialId, String quantity, String unitCost) throws Exception {
        return send("/receipts", """
                {"requestId":"%s","materialId":%d,"date":"2026-10-20","quantity":%s,"unitCost":%s,"reference":"GRN-1"}"""
                .formatted(requestId, materialId, quantity, unitCost));
    }

    private ResultActions receiveFrom(long materialId, long supplierId) throws Exception {
        return send("/receipts", """
                {"requestId":"%s","materialId":%d,"date":"2026-10-20","quantity":10,"unitCost":2500,"supplierId":%d}"""
                .formatted(UUID.randomUUID(), materialId, supplierId));
    }

    private ResultActions issue(long materialId, long projectId, String quantity) throws Exception {
        return issueWith(UUID.randomUUID(), materialId, projectId, quantity);
    }

    private ResultActions issueWith(UUID requestId, long materialId, long projectId, String quantity) throws Exception {
        return send("/issues", """
                {"requestId":"%s","materialId":%d,"projectId":%d,"date":"2026-10-20","quantity":%s,"reference":"GIN-1"}"""
                .formatted(requestId, materialId, projectId, quantity));
    }

    private ResultActions giveBack(long issueId, String quantity) throws Exception {
        return returnBody("{\"issueId\":%d".formatted(issueId), quantity);
    }

    private ResultActions returnBody(String start) throws Exception {
        return returnBody(start, "1");
    }

    private ResultActions returnBody(String start, String quantity) throws Exception {
        return send("/returns", start + ",\"requestId\":\"%s\",\"date\":\"2026-10-20\",\"quantity\":%s}".formatted(UUID.randomUUID(), quantity));
    }

    private ResultActions adjust(long materialId, String change, String reason, String unitCost) throws Exception {
        String costField = unitCost == null ? "" : ",\"unitCost\":" + unitCost;
        return send("/adjustments", """
                {"requestId":"%s","materialId":%d,"date":"2026-10-20","quantityChange":%s,"reason":"%s"%s}"""
                .formatted(UUID.randomUUID(), materialId, change, reason, costField));
    }

    private ResultActions send(String path, String body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(URL + path)
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions usage(long projectId) throws Exception {
        return mockMvc.perform(get(URL + "/projects/" + projectId + "/usage"));
    }

    private void expectStock(long materialId, String quantity, String value) throws Exception {
        mockMvc.perform(get(URL + "/materials/" + materialId))
                .andExpect(jsonPath("$.quantityOnHand").value(new BigDecimal(quantity).doubleValue()))
                .andExpect(jsonPath("$.stockValue").value(new BigDecimal(value).doubleValue()));
    }

    private void expectLayer(long layerId, String remainingQuantity, String remainingValue) {
        entityManager.flush();
        Map<String, Object> layer = jdbc.queryForMap(
                "SELECT remaining_quantity AS q, remaining_value AS v FROM stock_cost_layers WHERE id = ?", layerId);
        assertThat((BigDecimal) layer.get("q")).isEqualByComparingTo(remainingQuantity);
        assertThat((BigDecimal) layer.get("v")).isEqualByComparingTo(remainingValue);
    }

    private long layerOf(long movementId) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT id FROM stock_cost_layers WHERE source_movement_id = ?", Long.class, movementId);
    }

    // the open layers add up to the ledger balance, and every FIFO draw or return's allocations add up
    // to that movement's own quantity and cost
    private void assertLayersMatchTheLedger(long materialId) {
        entityManager.flush();
        Map<String, Object> last = jdbc.queryForMap(
                "SELECT quantity_after AS q, value_after AS v FROM stock_movements WHERE material_id = ? ORDER BY id DESC LIMIT 1", materialId);
        Map<String, Object> layers = jdbc.queryForMap(
                "SELECT sum(remaining_quantity) AS q, sum(remaining_value) AS v FROM stock_cost_layers WHERE material_id = ?", materialId);
        assertThat((BigDecimal) layers.get("q")).isEqualByComparingTo((BigDecimal) last.get("q"));
        assertThat((BigDecimal) layers.get("v")).isEqualByComparingTo((BigDecimal) last.get("v"));
        // a FIFO movement either created a layer (receipt, addition, legacy return) or is fully explained by its allocations
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM stock_movements m
                  LEFT JOIN (SELECT movement_id, sum(quantity) AS q, sum(cost) AS c FROM stock_cost_allocations GROUP BY movement_id) a
                    ON a.movement_id = m.id
                 WHERE m.material_id = ? AND m.costing_method = 'FIFO'
                   AND NOT EXISTS (SELECT 1 FROM stock_cost_layers l WHERE l.source_movement_id = m.id)
                   AND (a.q IS DISTINCT FROM m.quantity_change OR a.c IS DISTINCT FROM m.total_cost)""",
                Long.class, materialId)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM stock_cost_layers l JOIN stock_movements m ON m.id = l.source_movement_id
                 WHERE l.material_id = ? AND l.origin <> 'LEGACY_BALANCE'
                   AND (l.original_quantity <> m.quantity_change OR l.original_cost <> m.total_cost)""",
                Long.class, materialId)).isZero();
    }

    // a ledger row as the weighted-average code wrote it, before V4
    private long legacyMovement(String type, String change, String unitCost, String total, String qtyAfter, String valueAfter,
            Long projectId, Long returnedFrom) {
        return jdbc.queryForObject("""
                INSERT INTO stock_movements (material_id, movement_date, movement_type, quantity_change, unit_cost, total_cost,
                                             quantity_after, value_after, project_id, returned_from_movement_id, costing_method)
                VALUES (?, DATE '2026-10-01', ?, ?, ?, ?, ?, ?, ?, ?, 'WAC') RETURNING id""", Long.class, cement, type,
                new BigDecimal(change), new BigDecimal(unitCost), new BigDecimal(total), new BigDecimal(qtyAfter),
                new BigDecimal(valueAfter), projectId, returnedFrom);
    }

    private long supplier(String name) throws Exception {
        return id(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/suppliers")
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"%s\",\"phone\":\"0250000000\"}".formatted(name))));
    }

    private long project(long clientId, String type, String name) throws Exception {
        String json = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/projects").with(user("setup"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"%s","type":"%s","clientId":%d,"contractValue":100000,"startDate":"2026-01-01",
                         "expectedCompletion":"2026-12-31","schedule":[{"description":"All","dueDate":"2026-12-31","amount":100000}]}"""
                        .formatted(name, type, clientId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private List<String> activity(long projectId) {
        return jdbc.queryForList("SELECT message FROM activity_log WHERE project_id = ? AND kind = 'EXPENSE' ORDER BY id",
                String.class, projectId);
    }

    private List<Map<String, Object>> snapshot() {
        return List.of(
                Map.of("materials", jdbc.queryForList("SELECT * FROM materials ORDER BY id")),
                Map.of("movements", jdbc.queryForList("SELECT * FROM stock_movements ORDER BY id")),
                Map.of("layers", jdbc.queryForList("SELECT * FROM stock_cost_layers ORDER BY id")),
                Map.of("allocations", jdbc.queryForList("SELECT * FROM stock_cost_allocations ORDER BY id")),
                Map.of("activity", jdbc.queryForObject("SELECT count(*) FROM activity_log", Long.class)));
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
