package com.sondurukedella.settings.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sondurukedella.settings.dto.CommissionPolicyResponse;
import com.sondurukedella.settings.service.CommissionPolicyService;
import com.sondurukedella.settings.service.CompanyProfileService;
import com.sondurukedella.settings.service.ExpenseCategoryService;
import com.sondurukedella.settings.service.MeasurementUnitService;
import com.sondurukedella.settings.service.PaymentMethodService;
import com.sondurukedella.settings.service.RevenueCategoryService;
import com.sondurukedella.settings.service.WageSettingsService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web layer only: authorization, CSRF, request validation and the read-only revenue categories. */
@WebMvcTest(controllers = {
        SettingsController.class,
        ExpenseCategoryController.class,
        MeasurementUnitController.class,
        PaymentMethodController.class})
class SettingsControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompanyProfileService companyProfileService;
    @MockitoBean
    private CommissionPolicyService commissionPolicyService;
    @MockitoBean
    private WageSettingsService wageSettingsService;
    @MockitoBean
    private RevenueCategoryService revenueCategoryService;
    @MockitoBean
    private ExpenseCategoryService expenseCategoryService;
    @MockitoBean
    private MeasurementUnitService measurementUnitService;
    @MockitoBean
    private PaymentMethodService paymentMethodService;

    @Test
    void anonymousUsersCannotReadSettings() throws Exception {
        mockMvc.perform(get("/api/v1/settings").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(companyProfileService);
    }

    @Test
    void anonymousUsersCannotChangeSettings() throws Exception {
        mockMvc.perform(put("/api/v1/settings/commission-policy").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designCommissionPool\":20,\"version\":0}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(commissionPolicyService);
    }

    @Test
    @WithMockUser
    void changesWithoutACsrfTokenAreRejected() throws Exception {
        mockMvc.perform(put("/api/v1/settings/commission-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designCommissionPool\":20,\"version\":0}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(commissionPolicyService);
    }

    @Test
    @WithMockUser
    void authenticatedUsersCanUpdateTheCommissionPolicy() throws Exception {
        when(commissionPolicyService.update(any())).thenReturn(new CommissionPolicyResponse(
                new BigDecimal("25"), new BigDecimal("75"), null, 1L));

        mockMvc.perform(put("/api/v1/settings/commission-policy").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designCommissionPool\":25,\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designCompanyShare").value(75));
    }

    @Test
    @WithMockUser
    void commissionPoolMustBeBetweenZeroAndHundred() throws Exception {
        for (String pool : new String[] {"0", "100", "-5", "20.12345"}) {
            mockMvc.perform(put("/api/v1/settings/commission-policy").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"designCommissionPool\":" + pool + ",\"version\":0}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[*].field", hasItem("designCommissionPool")));
        }
        verifyNoInteractions(commissionPolicyService);
    }

    @Test
    @WithMockUser
    void wageSettingsAreValidated() throws Exception {
        mockMvc.perform(put("/api/v1/settings/wages").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultDailyRate":0,"hoursPerDay":13,"overtimeMultiplier":0,
                                 "payday":"SATURDAY","version":0}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("defaultDailyRate")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("hoursPerDay")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("overtimeMultiplier")));

        mockMvc.perform(put("/api/v1/settings/wages").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultDailyRate":3500,"hoursPerDay":8,"overtimeMultiplier":1.5,
                                 "payday":"SUNDAY","version":0}"""))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(wageSettingsService);
    }

    @Test
    @WithMockUser
    void companyProfileNeedsANameAndAValidEmail() throws Exception {
        mockMvc.perform(put("/api/v1/settings/company").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" ","email":"not-an-email","supplierCreditDays":-1,"version":0}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("supplierCreditDays")));
        verifyNoInteractions(companyProfileService);
    }

    @Test
    @WithMockUser
    void updatesWithoutAVersionAreRejected() throws Exception {
        mockMvc.perform(put("/api/v1/settings/commission-policy").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designCommissionPool\":20}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("version")));
    }

    @Test
    @WithMockUser
    void revenueCategoriesAreReadOnly() throws Exception {
        mockMvc.perform(post("/api/v1/settings/revenue-categories").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Rental\"}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/v1/settings/revenue-categories").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/v1/settings/revenue-categories").with(csrf()))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/v1/settings/revenue-categories/DESIGN").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void listEntriesNeedAName() throws Exception {
        mockMvc.perform(post("/api/v1/settings/units").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/settings/expense-categories").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Rent\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("group")));
        verifyNoInteractions(measurementUnitService, expenseCategoryService);
    }
}
