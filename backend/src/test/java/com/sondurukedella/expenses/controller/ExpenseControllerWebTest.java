package com.sondurukedella.expenses.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.expenses.dto.ExpensePaymentResponse;
import com.sondurukedella.expenses.dto.ExpensePaymentStatus;
import com.sondurukedella.expenses.entity.ExpenseScope;
import com.sondurukedella.expenses.service.ExpenseAttachmentService;
import com.sondurukedella.expenses.service.ExpensePaymentService;
import com.sondurukedella.expenses.service.ExpensePaymentService.PaymentResult;
import com.sondurukedella.expenses.service.ExpenseService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller slice only - services are mocked. Security, binding, validation and status codes.
@WebMvcTest(controllers = ExpenseController.class)
class ExpenseControllerWebTest {

    private static final String URL = "/api/v1/expenses";
    private static final String EXPENSE = """
            {"date":"2026-10-20","scope":"COMPANY","categoryId":14,"description":"Banner for the expo",
             "payee":"Print Shop","amount":12500}""";
    private static final String PAYMENT = """
            {"requestId":"2b7f9c1e-3a4d-4e5f-8a9b-0c1d2e3f4a5b","paymentDate":"2026-10-21","amount":5000,"paymentMethodId":1}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpenseService service;
    @MockitoBean
    private ExpensePaymentService paymentService;
    @MockitoBean
    private ExpenseAttachmentService attachmentService;

    @Test
    void anonymousUsersAreRejected() throws Exception {
        mockMvc.perform(get(URL).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL + "/1").accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(EXPENSE))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL + "/1/payments").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(PAYMENT))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service, paymentService);
    }

    @Test
    @WithMockUser
    void writesNeedACsrfToken() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(EXPENSE)).andExpect(status().isForbidden());
        mockMvc.perform(put(URL + "/1").contentType(MediaType.APPLICATION_JSON).content(EXPENSE)).andExpect(status().isForbidden());
        mockMvc.perform(post(URL + "/1/payments").contentType(MediaType.APPLICATION_JSON).content(PAYMENT))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(URL + "/1/payments/2/void").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service, paymentService);
    }

    @Test
    @WithMockUser
    void requiredFieldsAreReportedPerField() throws Exception {
        mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("date")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("scope")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("categoryId")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("description")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("payee")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("amount")));
        mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(EXPENSE.replace("12500", "-5")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("amount")));
        mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(EXPENSE.replace("12500", "12500.001")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("amount")));
        mockMvc.perform(put(URL + "/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(EXPENSE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("version")));
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    void malformedValuesAreBadRequests() throws Exception {
        mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(EXPENSE.replace("2026-10-20", "20/10/2026"))).andExpect(status().isBadRequest());
        mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(EXPENSE.replace("COMPANY", "Company"))).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("status", "Overdue")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("from", "2026-13-01")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL + "/abc")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    void paymentsNeedARequestIdDateAmountAndMethod() throws Exception {
        mockMvc.perform(post(URL + "/1/payments").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("requestId")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("paymentDate")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("amount")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("paymentMethodId")));
        mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(EXPENSE.replace("}", ",\"initialPayment\":{\"amount\":0}}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("initialPayment.amount")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("initialPayment.requestId")));
        verifyNoInteractions(service, paymentService);
    }

    @Test
    @WithMockUser
    void aNewPaymentIs201AndAResentOneIs200() throws Exception {
        ExpensePaymentResponse payment = new ExpensePaymentResponse(7L, 1L, LocalDate.of(2026, 10, 21),
                new BigDecimal("5000.00"), 1L, "Cash", null, null, UUID.randomUUID(), false, null, null,
                OffsetDateTime.parse("2026-10-24T10:00:00+05:30"), 0L);
        when(paymentService.record(eq(1L), any())).thenReturn(new PaymentResult(payment, true))
                .thenReturn(new PaymentResult(payment, false));

        mockMvc.perform(post(URL + "/1/payments").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(PAYMENT))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.method").value("Cash"));
        mockMvc.perform(post(URL + "/1/payments").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(PAYMENT))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void serviceErrorsMapTo400404And409() throws Exception {
        when(service.get(9L)).thenThrow(new NotFoundException("Expense not found."));
        when(service.create(any())).thenThrow(new InvalidRequestException("Choose a category from the Settings list."));
        when(service.update(eq(1L), any())).thenThrow(new ConflictException("The record was changed by someone else."));

        mockMvc.perform(get(URL + "/9")).andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Expense not found."));
        mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(EXPENSE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Choose a category from the Settings list."));
        mockMvc.perform(put(URL + "/1").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(EXPENSE.replace("}", ",\"version\":0}"))).andExpect(status().isConflict());
    }

    @Test
    @WithMockUser
    void listPassesFiltersAndDefaultsToTwentyPerPage() throws Exception {
        when(service.search(any(), any(Pageable.class))).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get(URL).param("scope", "PROJECT").param("projectId", "4").param("status", "OVERDUE")
                        .param("group", "CONSTRUCTION").param("from", "2026-10-01").param("to", "2026-10-31").param("q", "sand"))
                .andExpect(status().isOk());

        verify(service).search(argThat(f -> f.scope() == ExpenseScope.PROJECT && f.projectId() == 4L
                        && f.status() == ExpensePaymentStatus.OVERDUE && "sand".equals(f.q())
                        && LocalDate.of(2026, 10, 1).equals(f.from()) && LocalDate.of(2026, 10, 31).equals(f.to())),
                argThat(p -> p.getPageSize() == 20));
    }

    @Test
    @WithMockUser
    void expensesAndPaymentsCannotBeDeleted() throws Exception {
        mockMvc.perform(delete(URL + "/1").with(csrf())).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete(URL + "/1/payments/2").with(csrf())).andExpect(status().isNotFound());
        verifyNoInteractions(service, paymentService);
    }
}
