package com.sondurukedella.integration.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings API against the real schema (Flyway V1 + Hibernate validation). Every test runs in a transaction
 * that is rolled back, so no settings are changed in the database the tests run against.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMIN")
class SettingsApiIntegrationTest {

    private static final String SETTINGS = "/api/v1/settings";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void overviewReturnsAllSectionsWithTheFixedRevenueCategories() throws Exception {
        mockMvc.perform(get(SETTINGS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company.name").isNotEmpty())
                .andExpect(jsonPath("$.company.financialYearStartMonth").isNumber())
                .andExpect(jsonPath("$.commission.designCommissionPool").isNumber())
                .andExpect(jsonPath("$.wages.payday").isString())
                .andExpect(jsonPath("$.revenueCategories[*].code")
                        .value(containsInAnyOrder("DESIGN", "CONSTRUCTION", "CONSULTATION", "OTHER_INCOME")))
                .andExpect(jsonPath("$.revenueCategories[*].name", hasItem("Other Income")))
                .andExpect(jsonPath("$.expenseCategories").isNotEmpty())
                .andExpect(jsonPath("$.units").isNotEmpty())
                .andExpect(jsonPath("$.paymentMethods").isNotEmpty());
    }

    @Test
    void companyProfileUpdatesUseOptimisticLocking() throws Exception {
        long version = readLong(get(SETTINGS + "/company"), "$.version");
        String body = """
                {"name":"Sonduru Kedella","tagline":"Home Design & Construction","address":"Anuradhapura",
                 "phone":"025 000 0000","email":"accounts@example.com","registration":"PV 1",
                 "owner":"Owner","supplierCreditDays":21,"version":%d}""";

        mockMvc.perform(put(SETTINGS + "/company").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplierCreditDays").value(21))
                .andExpect(jsonPath("$.version").value(version + 1));

        mockMvc.perform(put(SETTINGS + "/company").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted(version)))
                .andExpect(status().isConflict());
    }

    @Test
    void anAdminCanReadButNotChangeTheCommissionPolicy() throws Exception {
        long policyVersion = readLong(get(SETTINGS + "/commission-policy"), "$.version");
        String pool = jdbc.queryForObject("SELECT design_commission_pool_pct::text FROM commission_policy WHERE id = 1", String.class);
        mockMvc.perform(put(SETTINGS + "/commission-policy").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designCommissionPool\":35,\"version\":" + policyVersion + "}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT design_commission_pool_pct::text FROM commission_policy WHERE id = 1", String.class))
                .isEqualTo(pool);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void commissionPolicyAndWagesCanBeSaved() throws Exception {
        long policyVersion = readLong(get(SETTINGS + "/commission-policy"), "$.version");
        mockMvc.perform(put(SETTINGS + "/commission-policy").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designCommissionPool\":18.5,\"constructionDefaultRate\":6,\"version\":"
                                + policyVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designCommissionPool").value(18.5))
                .andExpect(jsonPath("$.designCompanyShare").value(81.5))
                .andExpect(jsonPath("$.constructionDefaultRate").value(6));

        long wageVersion = readLong(get(SETTINGS + "/wages"), "$.version");
        mockMvc.perform(put(SETTINGS + "/wages").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultDailyRate":3750.50,"hoursPerDay":8,"overtimeMultiplier":1.25,
                                 "payday":"FRIDAY","version":%d}""".formatted(wageVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultDailyRate").value(3750.50))
                .andExpect(jsonPath("$.payday").value("FRIDAY"));
    }

    @Test
    void unitsAreAddedRemovedAndReactivated() throws Exception {
        String unitsUrl = SETTINGS + "/units";
        long id = readLong(post(unitsUrl).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test Unit\"}"), "$.id", 201);

        mockMvc.perform(post(unitsUrl).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test unit\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(delete(unitsUrl + "/" + id).with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(get(unitsUrl)).andExpect(jsonPath("$[*].name", not(hasItem("Test Unit"))));

        mockMvc.perform(post(unitsUrl).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Unit\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void theLastPaymentMethodCannotBeRemoved() throws Exception {
        String url = SETTINGS + "/payment-methods";
        List<Integer> ids = JsonPath.read(content(get(url)), "$[*].id");
        for (int i = 0; i < ids.size() - 1; i++) {
            mockMvc.perform(delete(url + "/" + ids.get(i)).with(csrf())).andExpect(status().isNoContent());
        }

        mockMvc.perform(delete(url + "/" + ids.get(ids.size() - 1)).with(csrf()))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(get(url)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void lockedExpenseCategoriesCannotBeRemovedButCustomOnesCan() throws Exception {
        String url = SETTINGS + "/expense-categories";
        List<Integer> labourIds = JsonPath.read(content(get(url).param("group", "CONSTRUCTION")),
                "$[?(@.name == 'Labour')].id");
        mockMvc.perform(delete(url + "/" + labourIds.getFirst()).with(csrf()))
                .andExpect(status().isUnprocessableContent());

        long id = readLong(post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"group\":\"COMPANY\",\"name\":\"Office Rent\"}"), "$.id", 201);
        mockMvc.perform(delete(url + "/" + id).with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(get(url).param("group", "COMPANY"))
                .andExpect(jsonPath("$[*].name", not(hasItem("Office Rent"))));
    }

    @Test
    void revenueCategoriesCannotBeChanged() throws Exception {
        mockMvc.perform(post(SETTINGS + "/revenue-categories").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Rental\"}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get(SETTINGS + "/revenue-categories"))
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    @WithAnonymousUser
    void anonymousRequestsAreRejected() throws Exception {
        mockMvc.perform(get(SETTINGS).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
    }

    private String content(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private long readLong(org.springframework.test.web.servlet.RequestBuilder request, String path) throws Exception {
        return ((Number) JsonPath.read(content(request), path)).longValue();
    }

    private long readLong(org.springframework.test.web.servlet.RequestBuilder request, String path, int expectedStatus)
            throws Exception {
        ResultActions result = mockMvc.perform(request).andExpect(status().is(expectedStatus));
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
    }
}
