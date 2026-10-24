package com.sondurukedella.inventory.controller;

import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.inventory.dto.CostLayerResponse;
import com.sondurukedella.inventory.dto.InventoryValuationResponse;
import com.sondurukedella.inventory.dto.MaterialCreateRequest;
import com.sondurukedella.inventory.dto.MaterialResponse;
import com.sondurukedella.inventory.dto.MaterialStatusRequest;
import com.sondurukedella.inventory.dto.MaterialUpdateRequest;
import com.sondurukedella.inventory.dto.MovementFilter;
import com.sondurukedella.inventory.dto.ProjectMaterialUsageResponse;
import com.sondurukedella.inventory.dto.StockAdjustmentRequest;
import com.sondurukedella.inventory.dto.StockIssueRequest;
import com.sondurukedella.inventory.dto.StockMovementResponse;
import com.sondurukedella.inventory.dto.StockReceiptRequest;
import com.sondurukedella.inventory.dto.StockReturnRequest;
import com.sondurukedella.inventory.entity.MovementType;
import com.sondurukedella.inventory.service.MaterialService;
import com.sondurukedella.inventory.service.StockMovementService;
import com.sondurukedella.inventory.service.StockMovementService.MovementResult;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// Materials and the stock ledger. Movements are only ever added - there is no PUT or DELETE on them;
// a mistake is corrected with an adjustment, or a return for an issue. Materials are deactivated, not deleted.
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final MaterialService materials;
    private final StockMovementService movements;

    @GetMapping("/materials")
    public PageResponse<MaterialResponse> listMaterials(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20) Pageable pageable) {
        return materials.search(q, category, active, pageable);
    }

    @GetMapping("/materials/{id}")
    public MaterialResponse material(@PathVariable long id) {
        return materials.get(id);
    }

    @PostMapping("/materials")
    public ResponseEntity<MaterialResponse> createMaterial(@Valid @RequestBody MaterialCreateRequest request) {
        MaterialResponse created = materials.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/materials/{id}")
    public MaterialResponse updateMaterial(@PathVariable long id, @Valid @RequestBody MaterialUpdateRequest request) {
        return materials.update(id, request);
    }

    @PutMapping("/materials/{id}/status")
    public MaterialResponse setMaterialStatus(@PathVariable long id, @Valid @RequestBody MaterialStatusRequest request) {
        return materials.setActive(id, request);
    }

    // a material's ledger, newest first; the same as /movements?materialId=
    @GetMapping("/materials/{id}/movements")
    public PageResponse<StockMovementResponse> materialMovements(@PathVariable long id,
            @PageableDefault(size = 20) Pageable pageable) {
        materials.get(id); // 404 for an unknown material rather than an empty page
        return movements.search(new MovementFilter(id, null, null, null, null, null), pageable);
    }

    // the material's FIFO cost layers, next to be used first; openOnly=false includes used-up ones
    @GetMapping("/materials/{id}/layers")
    public List<CostLayerResponse> materialLayers(@PathVariable long id,
            @RequestParam(defaultValue = "true") boolean openOnly) {
        return movements.layers(id, openOnly);
    }

    @GetMapping("/valuation")
    public InventoryValuationResponse valuation() {
        return materials.valuation();
    }

    @GetMapping("/movements")
    public PageResponse<StockMovementResponse> listMovements(
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) MovementType type,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return movements.search(new MovementFilter(materialId, type, projectId, supplierId, from, to), pageable);
    }

    @GetMapping("/movements/{id}")
    public StockMovementResponse movement(@PathVariable long id) {
        return movements.get(id);
    }

    // 201 for a new movement, 200 when the same requestId was already posted (nothing new is saved)
    @PostMapping("/receipts")
    public ResponseEntity<StockMovementResponse> receive(@Valid @RequestBody StockReceiptRequest request) {
        return respond(movements.receive(request));
    }

    @PostMapping("/issues")
    public ResponseEntity<StockMovementResponse> issue(@Valid @RequestBody StockIssueRequest request) {
        return respond(movements.issue(request));
    }

    @PostMapping("/returns")
    public ResponseEntity<StockMovementResponse> returnStock(@Valid @RequestBody StockReturnRequest request) {
        return respond(movements.returnStock(request));
    }

    @PostMapping("/adjustments")
    public ResponseEntity<StockMovementResponse> adjust(@Valid @RequestBody StockAdjustmentRequest request) {
        return respond(movements.adjust(request));
    }

    // the stock part of a project's material cost (issued minus returned)
    @GetMapping("/projects/{projectId}/usage")
    public ProjectMaterialUsageResponse projectUsage(@PathVariable long projectId) {
        return movements.projectUsage(projectId);
    }

    private static ResponseEntity<StockMovementResponse> respond(MovementResult result) {
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.movement());
    }
}
