package com.sondurukedella.integration.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sondurukedella.config.ClockConfig;
import java.time.LocalDate;
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

// Other income and its receipts, and how they reach the reports. Rolls back.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMIN")
class OtherIncomeApiIntegrationTest {

    private static final String URL = "/api/v1/other-income";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private long cash;

    @BeforeEach
    void setUp() {
        cash = jdbc.queryForObject("SELECT id FROM payment_methods WHERE name = 'Cash'", Long.class);
    }

    @Test
    void incomeIsRecordedAndReceiptsSettleItOnce() throws Exception {
        long id = id(create("{\"date\":\"2026-10-20\",\"description\":\" Scrap sale \",\"payer\":\"  \",\"amount\":15000,\"dueDate\":\"2026-12-31\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Scrap sale"))
                .andExpect(jsonPath("$.payer").doesNotExist())
                // dated before today and unpaid, so already overdue - the due date it was sent with is ignored
                .andExpect(jsonPath("$.status").value("OVERDUE"))
                .andExpect(jsonPath("$.outstanding").value(15000.00)));

        receive(id, UUID.randomUUID(), "5000").andExpect(status().isCreated()).andExpect(jsonPath("$.method").value("Cash"));
        mockMvc.perform(get(URL + "/" + id)).andExpect(jsonPath("$.status").value("OVERDUE")).andExpect(jsonPath("$.outstanding").value(10000.00));
        receive(id, UUID.randomUUID(), "10000.01").andExpect(status().isBadRequest());

        UUID request = UUID.randomUUID();
        long first = id(receive(id, request, "10000").andExpect(status().isCreated()));
        assertThat(id(receive(id, request, "10000").andExpect(status().isOk()))).isEqualTo(first);
        receive(id, request, "1").andExpect(status().isConflict());
        mockMvc.perform(get(URL + "/" + id)).andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(post(URL + "/" + id + "/receipts/" + first + "/void").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Bounced\",\"version\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.voided").value(true));
        mockMvc.perform(post(URL + "/" + id + "/receipts/" + first + "/void").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"again\",\"version\":1}")).andExpect(status().isConflict());
        mockMvc.perform(get(URL + "/" + id)).andExpect(jsonPath("$.outstanding").value(10000.00));
        // 5,000 and the voided 10,000; the resend added nothing
        mockMvc.perform(get(URL + "/" + id + "/receipts")).andExpect(jsonPath("$.length()").value(2));
        receive(999_999_999L, UUID.randomUUID(), "1").andExpect(status().isNotFound());
    }

    @Test
    void incomeCanBeEditedButNotBelowWhatWasReceivedAndDeletedOnlyWithoutReceipts() throws Exception {
        long id = id(create("{\"date\":\"2026-10-20\",\"description\":\"Hall rent\",\"amount\":6000}"));
        String edit = "{\"version\":%d,\"date\":\"2026-10-21\",\"description\":\"Hall rent (Oct)\",\"amount\":%s,\"dueDate\":\"2026-11-30\"}";
        mockMvc.perform(put(URL + "/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(edit.formatted(0, "7000")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(7000.00)).andExpect(jsonPath("$.description").value("Hall rent (Oct)"));
        mockMvc.perform(put(URL + "/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(edit.formatted(0, "7000")))
                .andExpect(status().isConflict());
        receive(id, UUID.randomUUID(), "5000").andExpect(status().isCreated());
        mockMvc.perform(put(URL + "/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(edit.formatted(1, "4999")))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(delete(URL + "/" + id).with(csrf())).andExpect(status().isUnprocessableContent());

        long spare = id(create("{\"date\":\"2026-10-20\",\"description\":\"Typo\",\"amount\":10}"));
        mockMvc.perform(delete(URL + "/" + spare).with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(get(URL + "/" + spare)).andExpect(status().isNotFound());
    }

    @Test
    void invalidIncomeIsRejected() throws Exception {
        create("{\"date\":\"2026-10-20\",\"description\":\"X\",\"amount\":0}").andExpect(status().isBadRequest());
        create("{\"date\":\"2026-10-20\",\"description\":\" \",\"amount\":10}").andExpect(status().isBadRequest());
        create("{\"date\":\"2026-10-20\",\"description\":\"X\",\"amount\":10,\"dueDate\":\"2026-10-19\"}").andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("sort", "amount")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL + "/999999999")).andExpect(status().isNotFound());
    }

    @Test
    void overdueStatusAndTheSummaryFollowTheFilter() throws Exception {
        create("{\"date\":\"2026-09-01\",\"description\":\"Old rent\",\"amount\":3000,\"dueDate\":\"2026-09-10\"}").andExpect(status().isCreated());
        create("{\"date\":\"2026-09-02\",\"description\":\"No due date\",\"amount\":2000}").andExpect(status().isCreated());
        mockMvc.perform(get(URL).param("from", "2026-09-01").param("to", "2026-09-02").param("status", "OVERDUE"))
                .andExpect(jsonPath("$.totalElements").value(2)); // both unpaid after the day they were recorded, due date or not
        mockMvc.perform(get(URL + "/summary").param("from", "2026-09-01").param("to", "2026-09-02"))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.amount").value(5000.00))
                .andExpect(jsonPath("$.outstanding").value(5000.00))
                .andExpect(jsonPath("$.overdue").value(5000.00));
    }

    @Test
    void incomeRecordedTodayIsDueUntilTomorrow() throws Exception {
        String today = LocalDate.now(ClockConfig.BUSINESS_ZONE).toString();
        create("{\"date\":\"" + today + "\",\"description\":\"Site visit fee\",\"amount\":4000}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DUE"));
    }

    @Test
    void theReportsCountOtherIncomeApartFromProjectRevenue() throws Exception {
        String dashBefore = body("/api/v1/reports/dashboard");
        String sumBefore = body("/api/v1/reports/financial-summary?from=2026-10-01&to=2026-10-31");
        long id = id(create("{\"date\":\"2026-10-20\",\"description\":\"Consulting talk\",\"amount\":8000}"));
        receive(id, UUID.randomUUID(), "3000").andExpect(status().isCreated());
        String dash = body("/api/v1/reports/dashboard");
        String sum = body("/api/v1/reports/financial-summary?from=2026-10-01&to=2026-10-31");

        assertThat(delta(dashBefore, dash, "$.revenue.otherIncome")).isEqualTo(8000);
        assertThat(delta(dashBefore, dash, "$.revenue.otherIncomeReceived")).isEqualTo(3000);
        assertThat(delta(dashBefore, dash, "$.receivables.otherIncomeOutstanding")).isEqualTo(5000);
        assertThat(delta(dashBefore, dash, "$.revenue.revenueRecorded")).isZero(); // project revenue is untouched
        assertThat(delta(sumBefore, sum, "$.revenue.otherIncome")).isEqualTo(8000);
        assertThat(delta(sumBefore, sum, "$.revenue.otherIncomeReceived")).isEqualTo(3000); // received on 2026-10-21
        assertThat(delta(sumBefore, sum, "$.position.otherIncomeOutstanding")).isEqualTo(5000);
    }

    private ResultActions create(String json) throws Exception {
        return mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions receive(long id, UUID requestId, String amount) throws Exception {
        return mockMvc.perform(post(URL + "/" + id + "/receipts").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"%s\",\"paymentDate\":\"2026-10-21\",\"amount\":%s,\"paymentMethodId\":%d}".formatted(requestId, amount, cash)));
    }

    private String body(String url) throws Exception {
        return mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private static double delta(String before, String after, String path) {
        return ((Number) JsonPath.read(after, path)).doubleValue() - ((Number) JsonPath.read(before, path)).doubleValue();
    }
}
