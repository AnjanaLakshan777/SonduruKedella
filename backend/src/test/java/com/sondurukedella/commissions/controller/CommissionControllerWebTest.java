package com.sondurukedella.commissions.controller;

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

import com.sondurukedella.commissions.dto.CommissionPaymentResponse;
import com.sondurukedella.commissions.service.CommissionPaymentService;
import com.sondurukedella.commissions.service.CommissionPaymentService.PaymentResult;
import com.sondurukedella.commissions.service.CommissionService;
import com.sondurukedella.common.exception.BusinessRuleException;
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

// Controller slice only - the services are mocked, so these tests just cover security, validation and status codes.
@WebMvcTest(controllers = {CommissionController.class, ProjectCommissionController.class})
class CommissionControllerWebTest {

    private static final String PAYMENT = """
            {"requestId":"%s","paymentDate":"2026-10-20","amount":%s,"paymentMethodId":1}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommissionService service;

    @MockitoBean
    private CommissionPaymentService paymentService;

    @Test
    void anonymousCallersAreRefused() throws Exception {
        mockMvc.perform(get("/api/v1/commissions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/projects/1/commissions")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/commissions/1/payments").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(PAYMENT.formatted(UUID.randomUUID(), "10"))).andExpect(status().isUnauthorized());
        verifyNoInteractions(service, paymentService);
    }

    @Test
    @WithMockUser
    void writesNeedACsrfToken() throws Exception {
        mockMvc.perform(put("/api/v1/projects/1/commissions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirm\":false,\"arrangements\":[]}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/commissions/1/payments").contentType(MediaType.APPLICATION_JSON)
                .content(PAYMENT.formatted(UUID.randomUUID(), "10"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/commissions/1/void").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"x\",\"version\":0}")).andExpect(status().isForbidden());
        verifyNoInteractions(service, paymentService);
    }

    @Test
    @WithMockUser
    void invalidInputIsRejectedBeforeTheService() throws Exception {
        mockMvc.perform(put("/api/v1/projects/1/commissions").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\":true,\"arrangements\":[{\"recipient\":\"Nimal\",\"ratePct\":120}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("arrangements[0].ratePct"));
        mockMvc.perform(put("/api/v1/projects/1/commissions").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arrangements\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("confirm"));
        mockMvc.perform(post("/api/v1/commissions/1/payments").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(PAYMENT.formatted(UUID.randomUUID(), "0"))).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/commissions/1/payments").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentDate\":\"2026-10-20\",\"amount\":10,\"paymentMethodId\":1}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/commissions/1/void").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\" \",\"version\":0}")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/commissions").param("status", "SETTLED")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/commissions").param("type", "CONSULTING")).andExpect(status().isBadRequest());
        verifyNoInteractions(service, paymentService);
    }

    @Test
    @WithMockUser
    void aNewPaymentIs201AndAResentOneIs200() throws Exception {
        CommissionPaymentResponse payment = new CommissionPaymentResponse(5, 1, 9, "Nimal", LocalDate.of(2026, 10, 20),
                new BigDecimal("10.00"), 1, "Cash", null, null, UUID.randomUUID(), false, null, null, OffsetDateTime.now(), 0);
        when(paymentService.record(eq(1L), any())).thenReturn(new PaymentResult(payment, true), new PaymentResult(payment, false));
        UUID request = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/commissions/1/payments").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(PAYMENT.formatted(request, "10"))).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(5));
        mockMvc.perform(post("/api/v1/commissions/1/payments").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(PAYMENT.formatted(request, "10"))).andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void businessRulesAre422AndThereIsNoDelete() throws Exception {
        when(service.voidArrangement(anyLong(), any())).thenThrow(new BusinessRuleException("Void its payments first"));
        mockMvc.perform(post("/api/v1/commissions/1/void").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"wrong\",\"version\":0}")).andExpect(status().isUnprocessableContent());
        mockMvc.perform(delete("/api/v1/commissions/1").with(csrf())).andExpect(status().isMethodNotAllowed());
    }
}
