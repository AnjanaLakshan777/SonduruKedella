package com.sondurukedella.inventory.controller;

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

import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.inventory.dto.StockMovementResponse;
import com.sondurukedella.inventory.entity.CostingMethod;
import com.sondurukedella.inventory.entity.MovementType;
import com.sondurukedella.inventory.service.MaterialService;
import com.sondurukedella.inventory.service.StockMovementService;
import com.sondurukedella.inventory.service.StockMovementService.MovementResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller slice only - services are mocked. Security, binding, validation and status codes.
@WebMvcTest(controllers = InventoryController.class)
class InventoryControllerWebTest {

    private static final String URL = "/api/v1/inventory";
    private static final String RECEIPT = """
            {"requestId":"5c0a7d2e-1b3c-4d5e-8f9a-0b1c2d3e4f5a","materialId":1,"date":"2026-10-20",
             "quantity":100,"unitCost":2500}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MaterialService materials;
    @MockitoBean
    private StockMovementService movements;

    @Test
    void anonymousUsersAreRejected() throws Exception {
        for (String path : new String[] {"/materials", "/materials/1", "/materials/1/layers", "/movements", "/valuation", "/projects/1/usage"}) {
            mockMvc.perform(get(URL + path).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post(URL + "/receipts").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(RECEIPT))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(materials, movements);
    }

    @Test
    @WithMockUser
    void everyWriteNeedsACsrfToken() throws Exception {
        for (String path : new String[] {"/materials", "/receipts", "/issues", "/returns", "/adjustments"}) {
            mockMvc.perform(post(URL + path).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        }
        mockMvc.perform(put(URL + "/materials/1").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(put(URL + "/materials/1/status").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(materials, movements);
    }

    @Test
    @WithMockUser
    void materialsNeedSkuNameCategoryAndUnit() throws Exception {
        mockMvc.perform(post(URL + "/materials").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\" \",\"defaultUnitCost\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", Matchers.hasItems("sku", "name", "category", "unitId", "defaultUnitCost")));
        mockMvc.perform(put(URL + "/materials/1").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"A\",\"name\":\"B\",\"category\":\"C\",\"unitId\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("version")));
        verifyNoInteractions(materials);
    }

    @Test
    @WithMockUser
    void movementsValidateQuantitiesCostsAndKeys() throws Exception {
        mockMvc.perform(post(URL + "/receipts").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0,\"unitCost\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", Matchers.hasItems("requestId", "materialId", "date", "quantity", "unitCost")));
        mockMvc.perform(post(URL + "/receipts").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(RECEIPT.replace("\"quantity\":100", "\"quantity\":1.0005")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("quantity")));
        mockMvc.perform(post(URL + "/issues").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":-3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", Matchers.hasItems("requestId", "materialId", "projectId", "date", "quantity")));
        mockMvc.perform(post(URL + "/returns").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("issueId")));
        mockMvc.perform(post(URL + "/adjustments").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \",\"unitCost\":-1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", Matchers.hasItems("reason", "quantityChange", "unitCost")));
        mockMvc.perform(get(URL + "/movements").param("type", "Issued")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL + "/movements").param("from", "2026-02-30")).andExpect(status().isBadRequest());
        verifyNoInteractions(movements);
    }

    @Test
    @WithMockUser
    void aNewMovementIs201AndAResentOneIs200() throws Exception {
        StockMovementResponse receipt = new StockMovementResponse(5L, 1L, "Cement", LocalDate.of(2026, 10, 20),
                MovementType.RECEIVED, new BigDecimal("100.000"), new BigDecimal("2500.0000"), new BigDecimal("250000.00"),
                new BigDecimal("100.000"), new BigDecimal("250000.00"), null, null, null, null, null, null, null, null,
                UUID.randomUUID(), OffsetDateTime.parse("2026-10-24T10:00:00+05:30"), CostingMethod.FIFO, 9L, List.of());
        when(movements.receive(any())).thenReturn(new MovementResult(receipt, true)).thenReturn(new MovementResult(receipt, false));

        mockMvc.perform(post(URL + "/receipts").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(RECEIPT))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("RECEIVED"))
                .andExpect(jsonPath("$.costingMethod").value("FIFO")).andExpect(jsonPath("$.costLayerId").value(9));
        mockMvc.perform(post(URL + "/receipts").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(RECEIPT))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(5));
    }

    @Test
    @WithMockUser
    void serviceErrorsMapTo400404And409() throws Exception {
        when(materials.get(9L)).thenThrow(new NotFoundException("Material not found."));
        when(movements.issue(any())).thenThrow(new InvalidRequestException("Only 5 bag of Cement in stock."));
        when(movements.receive(any())).thenThrow(new ConflictException("This request id was already used for a different stock movement."));

        mockMvc.perform(get(URL + "/materials/9")).andExpect(status().isNotFound());
        mockMvc.perform(post(URL + "/issues").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"requestId":"5c0a7d2e-1b3c-4d5e-8f9a-0b1c2d3e4f5b","materialId":1,"projectId":2,"date":"2026-10-20","quantity":10}"""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Only 5 bag of Cement in stock."));
        mockMvc.perform(post(URL + "/receipts").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(RECEIPT))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser
    void theLedgerCannotBeEditedOrDeleted() throws Exception {
        mockMvc.perform(put(URL + "/movements/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete(URL + "/movements/1").with(csrf())).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete(URL + "/materials/1").with(csrf())).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(materials, movements);
    }
}
