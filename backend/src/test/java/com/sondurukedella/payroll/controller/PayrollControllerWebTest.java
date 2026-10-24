package com.sondurukedella.payroll.controller;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.payroll.dto.PayrollPaymentResponse;
import com.sondurukedella.payroll.service.PayrollService;
import com.sondurukedella.payroll.service.PayrollService.PaymentResult;
import com.sondurukedella.payroll.service.SalaryAdvanceService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PayrollController.class)
class PayrollControllerWebTest {

    private static final String WEEK = "/api/v1/payroll/weeks/2026-09-07/employees/1";
    private static final String PAYMENT = """
            {"requestId":"5c0a7d2e-1b3c-4d5e-8f9a-0b1c2d3e4f5a","paymentDate":"2026-09-12","amount":9000,"paymentMethodId":1}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PayrollService payroll;
    @MockitoBean
    private SalaryAdvanceService advances;

    @Test
    void anonymousUsersAreRejected() throws Exception {
        for (String path : new String[] {"/api/v1/payroll/weeks/2026-09-07", WEEK, "/api/v1/payroll/weeks?from=2026-09-07&to=2026-09-07",
                "/api/v1/payroll/employees/1/history", "/api/v1/payroll/projects/1/labour", "/api/v1/payroll/advances"}) {
            mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(payroll, advances);
    }

    @Test
    @WithMockUser
    void everyWriteNeedsACsrfToken() throws Exception {
        for (String path : new String[] {WEEK + "/payments", WEEK + "/payments/1/void", WEEK + "/close", WEEK + "/corrections",
                "/api/v1/payroll/advances", "/api/v1/payroll/advances/1/void"}) {
            mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        }
        mockMvc.perform(put(WEEK + "/adjustment").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(payroll, advances);
    }

    @Test
    @WithMockUser
    void requestsAreValidatedBeforeTheServices() throws Exception {
        mockMvc.perform(post(WEEK + "/payments").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItems("requestId", "paymentDate", "amount", "paymentMethodId")));
        mockMvc.perform(post(WEEK + "/payments").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(PAYMENT.replace("9000", "9000.001"))).andExpect(status().isBadRequest());
        mockMvc.perform(post(WEEK + "/corrections").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItems("correctionDate", "wageDelta", "reason")));
        mockMvc.perform(put(WEEK + "/adjustment").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"otherDeduction\":-5}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/payroll/advances").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItems("requestId", "employeeId", "paymentDate", "amount", "paymentMethodId")));
        mockMvc.perform(post(WEEK + "/payments/1/void").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/payroll/weeks/not-a-date")).andExpect(status().isBadRequest());
        verifyNoInteractions(payroll, advances);
    }

    @Test
    @WithMockUser
    void aNewPaymentIs201AndAResentOneIs200() throws Exception {
        PayrollPaymentResponse paid = new PayrollPaymentResponse(5L, 1L, LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 12),
                new BigDecimal("9000.00"), 1L, "Cash", null, null, false, null, null, UUID.randomUUID(), OffsetDateTime.now(), 0L);
        when(payroll.recordPayment(eq(LocalDate.of(2026, 9, 7)), eq(1L), any()))
                .thenReturn(new PaymentResult(paid, true)).thenReturn(new PaymentResult(paid, false));
        mockMvc.perform(post(WEEK + "/payments").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(PAYMENT))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value(9000.0));
        mockMvc.perform(post(WEEK + "/payments").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(PAYMENT))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(5));
    }

    @Test
    @WithMockUser
    void serviceErrorsMapTo400And409() throws Exception {
        when(payroll.recordPayment(any(), anyLong(), any())).thenThrow(new InvalidRequestException("More than the LKR 1.00 still to pay for this week."));
        when(payroll.close(any(), anyLong())).thenThrow(new ConflictException("This week is already paid or closed."));
        mockMvc.perform(post(WEEK + "/payments").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(PAYMENT))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("More than the LKR 1.00 still to pay for this week."));
        mockMvc.perform(post(WEEK + "/close").with(csrf())).andExpect(status().isConflict());
    }

    @Test
    @WithMockUser
    void paymentsAndAdvancesAreVoidedNeverDeleted() throws Exception {
        // no route deletes either one (there is not even a route at those paths)
        mockMvc.perform(delete(WEEK + "/payments/1").with(csrf())).andExpect(status().is4xxClientError());
        mockMvc.perform(delete("/api/v1/payroll/advances/1").with(csrf())).andExpect(status().is4xxClientError());
        verifyNoInteractions(payroll, advances);
    }
}
