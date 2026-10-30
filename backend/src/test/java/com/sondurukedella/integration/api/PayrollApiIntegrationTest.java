package com.sondurukedella.integration.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
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
import java.time.LocalDate;
import java.util.List;
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

// Weekly payroll against the real schema (V1 triggers included). Each test rolls back. Real-commit
// races are in PayrollConcurrencyIntegrationTest.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMIN")
class PayrollApiIntegrationTest {

    private static final String URL = "/api/v1/payroll";
    // past weeks (payday Saturday, the default): payable today
    private static final LocalDate W1 = LocalDate.of(2026, 9, 7);
    private static final LocalDate W2 = W1.plusWeeks(1);
    private static final LocalDate W3 = W1.plusWeeks(2);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private long cash;
    private long house;
    private long kamal;

    @BeforeEach
    void setUp() throws Exception {
        cash = jdbc.queryForObject("SELECT id FROM payment_methods WHERE name = 'Cash'", Long.class);
        long client = jdbc.queryForObject("INSERT INTO clients (name, phone) VALUES ('Payroll Client', '0710000000') RETURNING id", Long.class);
        house = ApiFixtures.project(mockMvc, client, "CONSTRUCTION", "House");
        ApiFixtures.setProjectStatus(mockMvc, house, "IN_PROGRESS", 0);
        kamal = employee("Kamal", "3000", "560");
    }

    @Test
    void aWeekIsWorkedOutFromAttendanceAndApprovedOvertime() throws Exception {
        for (int d = 0; d < 5; d++) {
            mark(W1.plusDays(d), kamal, "PRESENT", house);
        }
        mark(W1.plusDays(5), kamal, "HALF_DAY", null);
        approve(overtime(kamal, W1.plusDays(1), "2", house));
        overtime(kamal, W1.plusDays(2), "1", house); // not approved: not paid

        mockMvc.perform(get(URL + "/weeks/" + W1))
                .andExpect(jsonPath("$.weekEnd").value("2026-09-12"))
                .andExpect(jsonPath("$.payable").value(true))
                .andExpect(jsonPath("$.workers").value(1))
                .andExpect(jsonPath("$.lines[0].status").value("READY_TO_PAY"))
                .andExpect(jsonPath("$.lines[0].payableDays").value(5.5))
                .andExpect(jsonPath("$.lines[0].basePay").value(16500.0))
                .andExpect(jsonPath("$.lines[0].overtimePay").value(1120.0))
                .andExpect(jsonPath("$.lines[0].pendingOvertimeHours").value(1.0))
                .andExpect(jsonPath("$.lines[0].grossPay").value(17620.0))
                .andExpect(jsonPath("$.lines[0].netPay").value(17620.0))
                .andExpect(jsonPath("$.lines[0].costs[*].projectId").value(Matchers.contains(null, (int) house)))
                .andExpect(jsonPath("$.lines[0].costs[*].amount").value(Matchers.contains(1500.0, 16120.0)))
                .andExpect(jsonPath("$.grossPay").value(17620.0));
        mockMvc.perform(get(URL + "/weeks/2026-09-08")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Monday")));
    }

    @Test
    void theFirstPaymentFreezesTheWeekAndLaterOnesAreCappedAtWhatIsOwed() throws Exception {
        threeDays(kamal, W1); // 9,000
        approve(overtime(kamal, W1, "1", house));    // + 560
        UUID first = UUID.randomUUID();
        long paymentId = id(pay(W1, kamal, "5000", first).andExpect(status().isCreated()));
        pay(W1, kamal, "5000", first).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(paymentId));
        pay(W1, kamal, "4000", first).andExpect(status().isConflict());

        line(W1, kamal).andExpect(jsonPath("$.line.frozen").value(true))
                .andExpect(jsonPath("$.line.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.line.paid").value(5000.0)).andExpect(jsonPath("$.line.remaining").value(4560.0))
                .andExpect(jsonPath("$.payments.length()").value(1));
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT net_pay FROM payroll_lines WHERE employee_id = ?", BigDecimal.class, kamal))
                .isEqualByComparingTo("9560.00");
        assertThat(jdbc.queryForObject("SELECT sum(amount) FROM payroll_line_costs c JOIN payroll_lines l ON l.id = c.payroll_line_id "
                + "WHERE l.employee_id = ?", BigDecimal.class, kamal)).isEqualByComparingTo("9560.00");

        // frozen: the week's days, overtime and adjustment can't change any more
        refusedByTheDatabase(() -> mark(W1, kamal, "ABSENT", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("has been paid"))));
        mark(W1, kamal, "PRESENT", house).andExpect(status().isOk()); // the same register again changes nothing
        refusedByTheDatabase(() -> overtime(kamal, W1.plusDays(3), "2", house).andExpect(status().isConflict()));
        adjust(W1, kamal, "{\"otherDeduction\":100}").andExpect(status().isConflict());
        // ... but the next week is open
        mark(W2, kamal, "PRESENT", house).andExpect(status().isOk());

        pay(W1, kamal, "4560.01", UUID.randomUUID()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("More than the LKR 4,560.00 still to pay for this week."));
        pay(W1, kamal, "4560", UUID.randomUUID()).andExpect(status().isCreated());
        line(W1, kamal).andExpect(jsonPath("$.line.status").value("PAID")).andExpect(jsonPath("$.line.remaining").value(0));
        pay(W1, kamal, "1", UUID.randomUUID()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Nothing is left to pay for this week."));
    }

    @Test
    void aWeekCanOnlyBePaidOncePaydayHasCome() throws Exception {
        LocalDate future = LocalDate.of(2030, 1, 7);
        approve(overtime(kamal, future.plusDays(1), "2", house));
        line(future, kamal).andExpect(jsonPath("$.line.status").value("DRAFT"))
                .andExpect(jsonPath("$.line.blockedReason").value("Payday for this week is 2030-01-12."));
        pay(future, kamal, "100", UUID.randomUUID()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Payday")));
        pay(W1, kamal, "100", UUID.randomUUID()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Nothing was earned this week."));
    }

    @Test
    void advancesAreRecoveredOldestFirstAndOnlyWhenTheWeekIsPaid() throws Exception {
        long older = id(advance(kamal, "2026-09-01", "2000"));
        long newer = id(advance(kamal, "2026-09-09", "3000"));
        long late = id(advance(kamal, "2026-09-20", "1000")); // after W1 and W2's paydays
        threeDays(kamal, W1);   // 9,000

        line(W1, kamal).andExpect(jsonPath("$.line.advancesOutstanding").value(5000.0))
                .andExpect(jsonPath("$.line.advanceDeduction").value(5000.0)).andExpect(jsonPath("$.line.netPay").value(4000.0));
        pay(W1, kamal, "4000", UUID.randomUUID()).andExpect(status().isCreated());
        line(W1, kamal).andExpect(jsonPath("$.recoveries[*].advanceId").value(Matchers.contains((int) older, (int) newer)))
                .andExpect(jsonPath("$.recoveries[*].amount").value(Matchers.contains(2000.0, 3000.0)));
        mockMvc.perform(get(URL + "/advances").param("employeeId", String.valueOf(kamal)))
                .andExpect(jsonPath("$.content[*].outstanding").value(Matchers.contains(1000.0, 0.0, 0.0)));

        threeDays(kamal, W2);
        line(W2, kamal).andExpect(jsonPath("$.line.advanceDeduction").value(0)); // the late one isn't due yet
        threeDays(kamal, W3);
        line(W3, kamal).andExpect(jsonPath("$.line.advanceDeduction").value(1000.0));

        voidAdvance(older, 0).andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(containsString("recovered")));
        voidAdvance(late, 0).andExpect(status().isOk()).andExpect(jsonPath("$.voided").value(true));
        line(W3, kamal).andExpect(jsonPath("$.line.advanceDeduction").value(0));
        mockMvc.perform(get(URL + "/employees/" + kamal + "/history").param("from", W1.toString()).param("to", W3.toString()))
                .andExpect(jsonPath("$.weeks[*].weekStart").value(Matchers.contains("2026-09-21", "2026-09-14", "2026-09-07")))
                .andExpect(jsonPath("$.advancesOutstanding").value(0));
    }

    @Test
    void anAdvanceIsIdempotentAndOnlyForActiveWorkers() throws Exception {
        UUID requestId = UUID.randomUUID();
        long id = id(advanceWith(requestId, kamal, "2026-09-01", "2000").andExpect(status().isCreated()));
        advanceWith(requestId, kamal, "2026-09-01", "2000").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        advanceWith(requestId, kamal, "2026-09-01", "2500").andExpect(status().isConflict());
        mockMvc.perform(put("/api/v1/employees/" + kamal + "/status").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVE\",\"version\":0}")).andExpect(status().isOk());
        advance(kamal, "2026-09-02", "100").andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("inactive")));
    }

    @Test
    void theOwnersAdjustmentOverridesTheDeductions() throws Exception {
        advance(kamal, "2026-09-01", "5000");
        threeDays(kamal, W1);   // 9,000
        adjust(W1, kamal, "{\"advanceDeduction\":1000,\"otherDeduction\":500,\"otherNote\":\" Broken trowel \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.line.advanceDeduction").value(1000.0)).andExpect(jsonPath("$.line.otherDeduction").value(500.0))
                .andExpect(jsonPath("$.line.otherNote").value("Broken trowel")).andExpect(jsonPath("$.line.netPay").value(7500.0));
        adjust(W1, kamal, "{\"otherDeduction\":0}").andExpect(status().isConflict()); // it exists now: send its version
        adjust(W1, kamal, "{\"otherDeduction\":9500,\"version\":0}").andExpect(status().isOk())
                .andExpect(jsonPath("$.line.netPay").value(-5500.0)) // 9,000 - the full 5,000 advance - 9,500
                .andExpect(jsonPath("$.line.blockedReason").value(containsString("deductions")));
        pay(W1, kamal, "1", UUID.randomUUID()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("deductions")));
        adjust(W1, kamal, "{\"otherDeduction\":-1,\"version\":1}").andExpect(status().isBadRequest());
    }

    @Test
    void aWeekTheAdvanceSwallowsIsClosedSoTheRecoveryIsKept() throws Exception {
        advance(kamal, "2026-09-01", "9000");
        threeDays(kamal, W1);   // 9,000: all of it goes to the advance
        pay(W1, kamal, "1", UUID.randomUUID()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Close the week")));
        mockMvc.perform(post(URL + "/weeks/" + W1 + "/employees/" + kamal + "/close").with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.line.frozen").value(true))
                .andExpect(jsonPath("$.line.status").value("PAID")).andExpect(jsonPath("$.recoveries[0].amount").value(9000.0));
        mockMvc.perform(post(URL + "/weeks/" + W1 + "/employees/" + kamal + "/close").with(csrf())).andExpect(status().isConflict());
        threeDays(kamal, W2);
        line(W2, kamal).andExpect(jsonPath("$.line.advanceDeduction").value(0)); // not deducted a second time
        mockMvc.perform(post(URL + "/weeks/" + W2 + "/employees/" + kamal + "/close").with(csrf())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("record a payment instead")));
    }

    @Test
    void aVoidedPaymentStopsCountingButTheWeekStaysFrozen() throws Exception {
        threeDays(kamal, W1);
        long payment = id(pay(W1, kamal, "9000", UUID.randomUUID()));
        mockMvc.perform(post(URL + "/weeks/" + W1 + "/employees/" + kamal + "/payments/" + payment + "/void").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Paid the wrong worker\",\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.voided").value(true));
        line(W1, kamal).andExpect(jsonPath("$.line.frozen").value(true)).andExpect(jsonPath("$.line.status").value("READY_TO_PAY"))
                .andExpect(jsonPath("$.line.remaining").value(9000.0));
        mockMvc.perform(post(URL + "/weeks/" + W1 + "/employees/" + kamal + "/payments/" + payment + "/void").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Again\",\"version\":1}")).andExpect(status().isConflict());
        // the flat list the frontend loads, voided ones flagged; outside the range it's not there
        mockMvc.perform(get(URL + "/payments").param("from", W1.toString()).param("to", W1.toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].voided".formatted(payment)).value(hasItem(true)))
                .andExpect(jsonPath("$[?(@.id == %d)].employeeId".formatted(payment)).value(hasItem((int) kamal)))
                .andExpect(jsonPath("$[?(@.id == %d)].weekStart".formatted(payment)).value(hasItem(W1.toString())));
        mockMvc.perform(get(URL + "/payments").param("from", W1.plusWeeks(1).toString()))
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(payment)).isEmpty());
        // voiding the payment doesn't unfreeze the week, so the UI keeps it locked
        mockMvc.perform(get(URL + "/frozen-weeks").param("from", W1.toString()).param("to", W1.toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.employeeId == %d)].weekStart".formatted(kamal)).value(hasItem(W1.toString())));
        mockMvc.perform(get(URL + "/frozen-weeks").param("from", W1.plusWeeks(1).toString()))
                .andExpect(jsonPath("$[?(@.employeeId == %d)]".formatted(kamal)).isEmpty());
        // last: the database refusal aborts the test transaction
        mark(W1, kamal, "ABSENT", null).andExpect(status().isConflict());
    }

    @Test
    void adjustmentsAreListedForTheFrontend() throws Exception {
        threeDays(kamal, W1);
        adjust(W1, kamal, "{\"otherDeduction\":500,\"otherNote\":\"Broken trowel\"}").andExpect(status().isOk());
        mockMvc.perform(get(URL + "/adjustments").param("from", W1.toString()).param("to", W1.toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.employeeId == %d)].otherDeduction".formatted(kamal)).value(hasItem(500.0)))
                .andExpect(jsonPath("$[?(@.employeeId == %d)].otherNote".formatted(kamal)).value(hasItem("Broken trowel")))
                .andExpect(jsonPath("$[?(@.employeeId == %d)].advanceDeduction".formatted(kamal)).value(hasItem(nullValue())));
    }

    @Test
    void aResentCorrectionIsRecordedOnceAndAWrongOneIsVoidedNotEdited() throws Exception {
        threeDays(kamal, W1);
        pay(W1, kamal, "9000", UUID.randomUUID());
        String body = """
                {"requestId":"%s","correctionDate":"2026-10-01","wageDelta":1500,"projectId":%d,"reason":"Missed overtime"}""";
        UUID request = UUID.randomUUID();
        String url = URL + "/weeks/" + W1 + "/employees/" + kamal + "/corrections";
        mockMvc.perform(post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body.formatted(request, house))).andExpect(status().isCreated());
        mockMvc.perform(post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body.formatted(request, house))).andExpect(status().isOk());
        mockMvc.perform(post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body.replace("1500", "1600").formatted(request, house)))
                .andExpect(status().isConflict());
        long id = jdbc.queryForObject("SELECT id FROM payroll_corrections WHERE request_id = ?", Long.class, request);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payroll_corrections c JOIN payroll_lines l ON l.id = c.payroll_line_id WHERE l.employee_id = ?",
                Long.class, kamal)).isOne();

        mockMvc.perform(post(url + "/" + id + "/void").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Wrong amount\",\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.voided").value(true)).andExpect(jsonPath("$.voidReason").value("Wrong amount"));
        mockMvc.perform(post(url + "/" + id + "/void").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"again\",\"version\":1}"))
                .andExpect(status().isConflict());
        // the voided correction no longer counts: the week owes 9,000 again and the labour cost drops back
        line(W1, kamal).andExpect(jsonPath("$.line.corrections").value(0)).andExpect(jsonPath("$.line.owed").value(9000.0));
        mockMvc.perform(get(URL + "/projects/" + house + "/labour")).andExpect(jsonPath("$.corrections").value(0));
        mockMvc.perform(post(url + "/999999999/void").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\",\"version\":0}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void correctionsChangeWhatAPaidWeekOwesWithinItsLabourShares() throws Exception {
        threeDays(kamal, W1);   // 9,000, all on the house
        correct(W1, kamal, "500", house).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("Only a paid week")));
        pay(W1, kamal, "9000", UUID.randomUUID());

        correct(W1, kamal, "1500", house).andExpect(status().isCreated()).andExpect(jsonPath("$.wageDelta").value(1500.0));
        line(W1, kamal).andExpect(jsonPath("$.line.corrections").value(1500.0)).andExpect(jsonPath("$.line.owed").value(10500.0))
                .andExpect(jsonPath("$.line.status").value("PARTIALLY_PAID")).andExpect(jsonPath("$.line.remaining").value(1500.0));
        correct(W1, kamal, "-200", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("to no project")));
        correct(W1, kamal, "-10500.01", house).andExpect(status().isBadRequest());
        correct(W1, kamal, "0", house).andExpect(status().isBadRequest());
        long client = jdbc.queryForObject("SELECT client_id FROM projects WHERE id = ?", Long.class, house);
        correct(W1, kamal, "100", ApiFixtures.project(mockMvc, client, "DESIGN", "Plan")).andExpect(status().isBadRequest());

        // a negative correction can leave the worker overpaid: it shows, and nothing is owed
        correct(W1, kamal, "-2500", house).andExpect(status().isCreated());
        line(W1, kamal).andExpect(jsonPath("$.line.owed").value(8000.0)).andExpect(jsonPath("$.line.remaining").value(0))
                .andExpect(jsonPath("$.line.overpaid").value(1000.0)).andExpect(jsonPath("$.line.status").value("PAID"))
                .andExpect(jsonPath("$.corrections.length()").value(2));
    }

    @Test
    void projectLabourIsPaidWeeksPlusCorrectionsPlusOpenWeeksCountedOnce() throws Exception {
        long nimal = employee("Nimal", "3500", "660");
        threeDays(kamal, W1);                    // 9,000 on the house
        mark(W1.plusDays(3), nimal, "PRESENT", house);  // 3,500 on the house
        pay(W1, kamal, "9000", UUID.randomUUID());       // Kamal's week frozen, Nimal's still open
        correct(W1, kamal, "-1000", house);
        threeDays(kamal, W2);                    // open: 9,000
        approve(overtime(nimal, W2, "2", null));  // not on the house

        mockMvc.perform(get(URL + "/projects/" + house + "/labour"))
                .andExpect(jsonPath("$.paidWeeks").value(9000.0))
                .andExpect(jsonPath("$.corrections").value(-1000.0))
                .andExpect(jsonPath("$.openWeeks").value(12500.0))
                .andExpect(jsonPath("$.total").value(20500.0))
                .andExpect(jsonPath("$.weeks.length()").value(3));
        // advances and payments never add to it
        advance(kamal, "2026-09-15", "4000");
        mockMvc.perform(get(URL + "/projects/" + house + "/labour")).andExpect(jsonPath("$.total").value(20500.0));
        mockMvc.perform(get(URL + "/projects/" + Long.MAX_VALUE + "/labour")).andExpect(status().isNotFound());
    }

    @Test
    void thePeriodSummaryListsEachWeek() throws Exception {
        threeDays(kamal, W1);
        threeDays(kamal, W2);
        pay(W1, kamal, "9000", UUID.randomUUID());
        mockMvc.perform(get(URL + "/weeks").param("from", W1.toString()).param("to", W3.toString()))
                .andExpect(jsonPath("$[*].weekStart").value(Matchers.contains("2026-09-21", "2026-09-14", "2026-09-07")))
                .andExpect(jsonPath("$[*].grossPay").value(Matchers.contains(0.0, 9000.0, 9000.0)))
                .andExpect(jsonPath("$[*].remaining").value(Matchers.contains(0.0, 9000.0, 0.0)));
        mockMvc.perform(get(URL + "/weeks").param("from", "2025-01-06").param("to", "2026-09-07")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL + "/weeks").param("from", W2.toString()).param("to", W1.toString())).andExpect(status().isBadRequest());
    }

    @Test
    void theDatabaseGuardsPaidWeeks() throws Exception {
        threeDays(kamal, W1);
        pay(W1, kamal, "9000", UUID.randomUUID());
        entityManager.flush();
        assertRefused("UPDATE payroll_lines SET net_pay = 1 WHERE employee_id = ?", kamal);
        assertRefused("DELETE FROM payroll_payments WHERE payroll_line_id IN (SELECT id FROM payroll_lines WHERE employee_id = ?)", kamal);
        assertRefused("UPDATE payroll_line_costs SET amount = 1 WHERE payroll_line_id IN (SELECT id FROM payroll_lines WHERE employee_id = ?)", kamal);
        assertRefused("DELETE FROM attendance WHERE employee_id = ?", kamal);
        // and the same worker and week can't be frozen twice
        assertRefused("INSERT INTO payroll_lines (employee_id, week_start, week_end, present_days, half_days, payable_days, daily_wage, "
                + "base_pay, overtime_hours, overtime_pay, gross_pay, advance_deduction, other_deduction, net_pay) "
                + "VALUES (?, DATE '2026-09-07', DATE '2026-09-12', 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0)", kamal);
    }

    @Test
    @WithAnonymousUser
    void anonymousRequestsAreRejected() throws Exception {
        mockMvc.perform(get(URL + "/weeks/" + W1).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL + "/advances").accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
    }

    private long employee(String name, String wage, String ot) throws Exception {
        return id(mockMvc.perform(post("/api/v1/employees").with(user("setup").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"%s","role":"Labourer","dailyWage":%s,"overtimeRate":%s,"joinedOn":"2026-01-05"}""".formatted(name, wage, ot)))
                .andExpect(status().isCreated()));
    }

    // the register for a day with just this worker on it (enough for one worker per day in these tests)
    private ResultActions mark(LocalDate day, long employeeId, String status, Long projectId) throws Exception {
        List<String> others = jdbc.queryForList("SELECT format('{\"employeeId\":%s,\"status\":\"%s\"%s}', employee_id, status, "
                + "CASE WHEN project_id IS NULL THEN '' ELSE ',\"projectId\":' || project_id END) FROM attendance "
                + "WHERE work_date = ? AND employee_id <> ?", String.class, day, employeeId);
        String mine = "{\"employeeId\":%d,\"status\":\"%s\"%s}".formatted(employeeId, status, projectId == null ? "" : ",\"projectId\":" + projectId);
        String entries = String.join(",", others) + (others.isEmpty() ? "" : ",") + mine;
        entityManager.flush();
        return mockMvc.perform(put("/api/v1/attendance/" + day).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"entries\":[" + entries + "]}"));
    }

    private void threeDays(long employeeId, LocalDate weekStart) throws Exception {
        for (int d = 0; d < 3; d++) {
            mark(weekStart.plusDays(d), employeeId, "PRESENT", house).andExpect(status().isOk());
        }
    }

    private ResultActions overtime(long employeeId, LocalDate day, String hours, Long projectId) throws Exception {
        return mockMvc.perform(post("/api/v1/overtime").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                {"employeeId":%d,"date":"%s",%s"hours":%s,"recordedBy":"Supervisor"}"""
                .formatted(employeeId, day, projectId == null ? "" : "\"projectId\":" + projectId + ",", hours)));
    }

    private void approve(ResultActions overtime) throws Exception {
        long id = id(overtime.andExpect(status().isCreated()));
        mockMvc.perform(put("/api/v1/overtime/" + id + "/approval").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"approved\":true,\"version\":0}")).andExpect(status().isOk());
    }

    private ResultActions pay(LocalDate weekStart, long employeeId, String amount, UUID requestId) throws Exception {
        return mockMvc.perform(post(URL + "/weeks/" + weekStart + "/employees/" + employeeId + "/payments").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"requestId":"%s","paymentDate":"%s","amount":%s,"paymentMethodId":%d}"""
                .formatted(requestId, weekStart.plusDays(5), amount, cash)));
    }

    private ResultActions line(LocalDate weekStart, long employeeId) throws Exception {
        entityManager.flush();
        return mockMvc.perform(get(URL + "/weeks/" + weekStart + "/employees/" + employeeId));
    }

    private ResultActions adjust(LocalDate weekStart, long employeeId, String body) throws Exception {
        return mockMvc.perform(put(URL + "/weeks/" + weekStart + "/employees/" + employeeId + "/adjustment").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions correct(LocalDate weekStart, long employeeId, String delta, Long projectId) throws Exception {
        return mockMvc.perform(post(URL + "/weeks/" + weekStart + "/employees/" + employeeId + "/corrections").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"requestId":"%s","correctionDate":"2026-10-01","wageDelta":%s,%s"reason":"Missed overtime"}"""
                .formatted(java.util.UUID.randomUUID(), delta, projectId == null ? "" : "\"projectId\":" + projectId + ",")));
    }

    private ResultActions advance(long employeeId, String date, String amount) throws Exception {
        return advanceWith(UUID.randomUUID(), employeeId, date, amount);
    }

    private ResultActions advanceWith(UUID requestId, long employeeId, String date, String amount) throws Exception {
        return mockMvc.perform(post(URL + "/advances").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                {"requestId":"%s","employeeId":%d,"paymentDate":"%s","amount":%s,"paymentMethodId":%d}"""
                .formatted(requestId, employeeId, date, amount, cash)));
    }

    private ResultActions voidAdvance(long id, long version) throws Exception {
        return mockMvc.perform(post(URL + "/advances/" + id + "/void").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Entered twice\",\"version\":%d}".formatted(version)));
    }

    // A request the freeze trigger refuses aborts the test's (shared) transaction, so it runs inside a
    // savepoint and the persistence context is dropped after it. In the app each request is its own transaction.
    private void refusedByTheDatabase(ThrowingRunnable request) throws Exception {
        entityManager.flush();
        jdbc.execute("SAVEPOINT refused");
        request.run();
        jdbc.execute("ROLLBACK TO SAVEPOINT refused");
        entityManager.clear();
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    // a refused statement aborts a PostgreSQL transaction, so each one gets its own savepoint
    private void assertRefused(String sql, Object... args) {
        jdbc.execute("SAVEPOINT guard");
        assertThatThrownBy(() -> jdbc.update(sql, args)).isInstanceOf(DataAccessException.class);
        jdbc.execute("ROLLBACK TO SAVEPOINT guard");
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
