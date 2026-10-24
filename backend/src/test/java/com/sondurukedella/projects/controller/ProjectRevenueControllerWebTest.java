package com.sondurukedella.projects.controller;

import static org.hamcrest.Matchers.hasItem;
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
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.projects.service.ProjectRevenueService;
import com.sondurukedella.revenue.controller.ReceivablesController;
import com.sondurukedella.revenue.dto.ReceiptResponse;
import com.sondurukedella.revenue.dto.ReceivablesSummaryResponse;
import com.sondurukedella.revenue.service.ReceiptService.ReceiptResult;
import com.sondurukedella.revenue.service.ReceivableService;
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

// Controller slice only - services are mocked. Security, binding, validation and status codes.
@WebMvcTest(controllers = {ProjectRevenueController.class, ReceivablesController.class})
class ProjectRevenueControllerWebTest {

    private static final String BASE = "/api/v1/projects/4";
    private static final String RECEIPT = """
            {"requestId":"6f1c2b9e-1d2a-4c5b-9e8f-0a1b2c3d4e5f","paymentDate":"2026-10-20","amount":250000,
             "paymentMethodId":1,"reference":"TT 4471"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectRevenueService service;
    @MockitoBean
    private ReceivableService receivableService;

    @Test
    void anonymousUsersCannotReadOrChangeMoney() throws Exception {
        mockMvc.perform(get(BASE + "/receivables").accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/receivables/summary").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/installments/1/bill").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/installments/1/receipts").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(RECEIPT)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service, receivableService);
    }

    @Test
    @WithMockUser
    void everyMoneyChangeNeedsACsrfToken() throws Exception {
        for (String path : new String[] {"/installments/1/bill", "/installments/1/receipts", "/receipts/1/void",
                "/installments/1/write-offs", "/delay-terms/deduction", "/delay-terms/deduction/reverse"}) {
            mockMvc.perform(post(BASE + path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    void receiptsNeedARequestIdDateAmountAndMethod() throws Exception {
        mockMvc.perform(post(BASE + "/installments/1/receipts").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("requestId")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("paymentDate")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("amount")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("paymentMethodId")));
        mockMvc.perform(post(BASE + "/installments/1/receipts").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(RECEIPT.replace("250000", "250000.005")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("amount")));
        mockMvc.perform(post(BASE + "/installments/1/receipts").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(RECEIPT.replace("6f1c2b9e-1d2a-4c5b-9e8f-0a1b2c3d4e5f", "not-a-uuid")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    void aNewReceiptIs201AndAResentOneIs200() throws Exception {
        ReceiptResponse receipt = receipt();
        when(service.recordReceipt(eq(4L), eq(1L), any()))
                .thenReturn(new ReceiptResult(receipt, true)).thenReturn(new ReceiptResult(receipt, false));

        mockMvc.perform(post(BASE + "/installments/1/receipts").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(RECEIPT)).andExpect(status().isCreated()).andExpect(jsonPath("$.method").value("Bank Transfer"));
        mockMvc.perform(post(BASE + "/installments/1/receipts").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(RECEIPT)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(9));
    }

    @Test
    @WithMockUser
    void otherRequestsAreValidatedToo() throws Exception {
        mockMvc.perform(post(BASE + "/installments/1/bill").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("version")));
        mockMvc.perform(post(BASE + "/installments/1/write-offs").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("date")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("amount")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("reason")));
        mockMvc.perform(post(BASE + "/receipts/1/void").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("reason")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("version")));
        mockMvc.perform(post(BASE + "/delay-terms/deduction").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("installmentId")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("version")));
        mockMvc.perform(get(BASE + "/delay-terms/calculation").param("completionDate", "31/12/2026"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    void serviceErrorsMapTo404400And409() throws Exception {
        when(service.receivables(9L)).thenThrow(new NotFoundException("Project not found."));
        when(service.bill(eq(4L), anyLong(), any())).thenThrow(new ConflictException("This installment was already billed."));
        when(service.recordReceipt(eq(4L), anyLong(), any()))
                .thenThrow(new InvalidRequestException("More than the LKR 100,000.00 still owed on Advance."));

        mockMvc.perform(get("/api/v1/projects/9/receivables")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Project not found."));
        mockMvc.perform(post(BASE + "/installments/1/bill").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}")).andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        mockMvc.perform(post(BASE + "/installments/1/receipts").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(RECEIPT)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("More than the LKR 100,000.00 still owed on Advance."));
    }

    @Test
    @WithMockUser
    void financialRecordsCannotBeEditedOrDeleted() throws Exception {
        mockMvc.perform(delete(BASE + "/receipts/1").with(csrf())).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put(BASE + "/receipts/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete(BASE + "/installments/1/write-offs").with(csrf())).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    void theSummaryIsServedToSignedInUsers() throws Exception {
        when(receivableService.summary()).thenReturn(new ReceivablesSummaryResponse(new BigDecimal("350000.00"),
                new BigDecimal("350000.00"), BigDecimal.ZERO, BigDecimal.ZERO, 1, 1, 0));

        mockMvc.perform(get("/api/v1/receivables/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outstanding").value(350000.00))
                .andExpect(jsonPath("$.overdueInstallments").value(1));
    }

    private static ReceiptResponse receipt() {
        return new ReceiptResponse(9L, 1L, 4L, LocalDate.of(2026, 10, 20), new BigDecimal("250000.00"), 1L,
                "Bank Transfer", "TT 4471", null, UUID.randomUUID(), false, null, null,
                OffsetDateTime.parse("2026-10-24T10:00:00+05:30"), 0L);
    }
}
