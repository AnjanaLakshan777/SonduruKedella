package com.sondurukedella.inventory.service;

import com.sondurukedella.activity.entity.ActivityKind;
import com.sondurukedella.activity.service.ActivityService;
import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.inventory.dto.CostAllocationResponse;
import com.sondurukedella.inventory.dto.CostLayerResponse;
import com.sondurukedella.inventory.dto.MovementFilter;
import com.sondurukedella.inventory.dto.ProjectMaterialUsageResponse;
import com.sondurukedella.inventory.dto.StockAdjustmentRequest;
import com.sondurukedella.inventory.dto.StockIssueRequest;
import com.sondurukedella.inventory.dto.StockMovementResponse;
import com.sondurukedella.inventory.dto.StockReceiptRequest;
import com.sondurukedella.inventory.dto.StockReturnRequest;
import com.sondurukedella.inventory.entity.CostAllocation;
import com.sondurukedella.inventory.entity.CostLayer;
import com.sondurukedella.inventory.entity.CostLayerOrigin;
import com.sondurukedella.inventory.entity.CostingMethod;
import com.sondurukedella.inventory.entity.Material;
import com.sondurukedella.inventory.entity.MovementType;
import com.sondurukedella.inventory.entity.StockMovement;
import com.sondurukedella.inventory.entity.StockMovement.Posting;
import com.sondurukedella.inventory.mapper.InventoryMapper;
import com.sondurukedella.inventory.repository.CostAllocationRepository;
import com.sondurukedella.inventory.repository.CostLayerRepository;
import com.sondurukedella.inventory.repository.InventorySpecifications;
import com.sondurukedella.inventory.repository.MaterialRepository;
import com.sondurukedella.inventory.repository.StockMovementRepository;
import com.sondurukedella.inventory.repository.StockMovementRepository.IssueTotal;
import com.sondurukedella.inventory.repository.StockMovementRepository.ProjectUsageRow;
import com.sondurukedella.inventory.service.StockLedger.Balance;
import com.sondurukedella.inventory.validation.InventoryRules;
import com.sondurukedella.projects.dto.ProjectResponse;
import com.sondurukedella.projects.entity.ProjectStatus;
import com.sondurukedella.projects.entity.ProjectType;
import com.sondurukedella.projects.service.ProjectService;
import com.sondurukedella.settings.service.MeasurementUnitService;
import com.sondurukedella.suppliers.dto.SupplierResponse;
import com.sondurukedella.suppliers.service.SupplierService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Receipts, issues, returns and adjustments, costed FIFO. Each one: lock the material row, check the
// requestId, validate, then post through StockLedger, which prices it from the cost layers and saves
// the movement, layers and allocations together - one transaction, so a failure leaves no movement,
// no allocation and no changed layer. Projects and suppliers are checked through their services.
//
// Where cost lives, so nothing is counted twice:
//   - buying into stock (RECEIVED) adds a layer to stock value only; it is not a project cost or an expense;
//   - issuing to a project (ISSUED) is what makes it a project cost, at the FIFO cost fixed on the movement;
//   - returning (RETURNED) takes that same cost back off the project and puts it back in its layers.
@Service
@RequiredArgsConstructor
public class StockMovementService {

    public static final int MAX_PAGE_SIZE = 100;
    // the movement dialog only offers these, and only for construction projects
    private static final Set<ProjectStatus> ISSUABLE = EnumSet.of(ProjectStatus.PLANNING, ProjectStatus.IN_PROGRESS,
            ProjectStatus.ON_HOLD);

    private final MaterialRepository materials;
    private final StockMovementRepository movements;
    private final CostLayerRepository layers;
    private final CostAllocationRepository allocations;
    private final StockLedger ledger;
    private final InventoryMapper mapper;
    private final ProjectService projectService;
    private final SupplierService supplierService;
    private final MeasurementUnitService units;
    private final ActivityService activityService;

    @Transactional
    public MovementResult receive(StockReceiptRequest request) {
        Material material = lock(request.materialId());
        Optional<StockMovementResponse> replay = replay(request.requestId(), MovementType.RECEIVED, material.getId(),
                request.quantity(), null);
        if (replay.isPresent()) {
            return new MovementResult(replay.get(), false);
        }
        requireActive(material);
        if (request.supplierId() != null) {
            SupplierResponse supplier = supplierService.find(request.supplierId())
                    .orElseThrow(() -> new InvalidRequestException("The selected supplier does not exist."));
            if (!supplier.active()) {
                throw new InvalidRequestException(supplier.name() + " is inactive and can't be used for new receipts.");
            }
        }
        // every receipt (opening stock included) is its own FIFO layer at the price paid
        BigDecimal unitCost = request.unitCost().setScale(FifoCosting.UNIT_COST_SCALE);
        StockMovement saved = ledger.addLayer(material, new Posting(material.getId(), request.date(), MovementType.RECEIVED,
                request.quantity(), unitCost, FifoCosting.receiptCost(request.quantity(), unitCost), null, null,
                InventoryRules.trimToNull(request.notes()), InventoryRules.trimToNull(request.reference()),
                request.supplierId(), request.requestId()), CostLayerOrigin.RECEIPT);
        return new MovementResult(toResponse(saved), true);
    }

    @Transactional
    public MovementResult issue(StockIssueRequest request) {
        Material material = lock(request.materialId());
        Optional<StockMovementResponse> replay = replay(request.requestId(), MovementType.ISSUED, material.getId(),
                request.quantity(), request.projectId());
        if (replay.isPresent()) {
            return new MovementResult(replay.get(), false);
        }
        requireActive(material);
        ProjectResponse project = project(request.projectId());
        if (project.type() != ProjectType.CONSTRUCTION) {
            throw new InvalidRequestException("Materials can only be issued to construction projects.");
        }
        if (!ISSUABLE.contains(project.status())) {
            throw new InvalidRequestException("Project " + project.code() + " is " + project.status().label()
                    + " and can't take new material issues.");
        }

        Balance stock = ledger.balance(material.getId());
        if (request.quantity().compareTo(stock.quantity()) > 0) {
            throw new InvalidRequestException("Only " + InventoryRules.quantity(stock.quantity()) + " " + unitName(material)
                    + " of " + material.getName() + " in stock.");
        }
        // the cost is fixed here, from the oldest layers, and kept with its allocations, so later
        // purchase prices can never change what the project was charged
        StockMovement saved = ledger.drawFifo(material, new Posting(material.getId(), request.date(), MovementType.ISSUED,
                request.quantity().negate(), null, null, project.id(), null,
                InventoryRules.trimToNull(request.notes()), InventoryRules.trimToNull(request.reference()), null,
                request.requestId()));
        activityService.record(project.id(), ActivityKind.EXPENSE, "Materials issued: " + InventoryRules.quantity(request.quantity())
                + " " + unitName(material) + " " + material.getName() + " (" + Money.format(saved.getTotalCost().negate()) + ").");
        return new MovementResult(toResponse(saved), true);
    }

    // Allowed for an inactive material and for any project status: stock already out on a site can
    // always come back, and leftovers usually do return once a project finishes.
    @Transactional
    public MovementResult returnStock(StockReturnRequest request) {
        StockMovement issue = movements.findById(request.issueId())
                .filter(m -> m.getType() == MovementType.ISSUED)
                .orElseThrow(() -> new InvalidRequestException("The selected issue does not exist."));
        if (request.materialId() != null && !request.materialId().equals(issue.getMaterialId())) {
            throw new InvalidRequestException("That issue was for a different material.");
        }
        if (request.projectId() != null && !request.projectId().equals(issue.getProjectId())) {
            throw new InvalidRequestException("That issue was to a different project.");
        }
        Material material = lock(issue.getMaterialId());
        Optional<StockMovementResponse> replay = replay(request.requestId(), MovementType.RETURNED, material.getId(),
                request.quantity(), issue.getProjectId());
        if (replay.isPresent()) {
            return new MovementResult(replay.get(), false);
        }

        // read under the material lock, so two returns of the same issue can't both see the old total
        BigDecimal issued = issue.getQuantityChange().negate();
        BigDecimal returned = movements.sumReturnedQuantity(issue.getId());
        BigDecimal returnable = issued.subtract(returned);
        if (request.quantity().compareTo(returnable) > 0) {
            throw new InvalidRequestException("Only " + InventoryRules.quantity(returnable) + " " + unitName(material)
                    + " of this issue can still be returned.");
        }
        Posting posting = new Posting(material.getId(), request.date(), MovementType.RETURNED, request.quantity(), null, null,
                issue.getProjectId(), issue.getId(), InventoryRules.trimToNull(request.notes()),
                InventoryRules.trimToNull(request.reference()), null, request.requestId());
        StockMovement saved;
        if (issue.getCostingMethod() == CostingMethod.FIFO) {
            saved = ledger.returnToLayers(material, issue, posting);
        } else {
            // An issue from before FIFO has no layers to go back to. It comes back at its own recorded
            // cost, as it always did, as a new layer; the last return takes what is left of the issue's cost.
            BigDecimal cost = FifoCosting.share(request.quantity(), returnable,
                    issue.getTotalCost().negate().subtract(movements.sumReturnedCost(issue.getId())), issue.getUnitCost());
            saved = ledger.addLayer(material, posting.priced(issue.getUnitCost(), cost), CostLayerOrigin.LEGACY_RETURN);
        }
        activityService.record(issue.getProjectId(), ActivityKind.EXPENSE, "Materials returned to stock: "
                + InventoryRules.quantity(request.quantity()) + " " + unitName(material) + " " + material.getName()
                + " (" + Money.format(saved.getTotalCost()) + " off the project's costs).");
        return new MovementResult(toResponse(saved), true);
    }

    @Transactional
    public MovementResult adjust(StockAdjustmentRequest request) {
        InventoryRules.nonZero(request.quantityChange());
        String reason = InventoryRules.adjustmentReason(request.reason());
        Material material = lock(request.materialId());
        Optional<StockMovementResponse> replay = replay(request.requestId(), MovementType.ADJUSTMENT, material.getId(),
                request.quantityChange(), null);
        if (replay.isPresent()) {
            return new MovementResult(replay.get(), false);
        }
        requireActive(material);

        Posting posting = new Posting(material.getId(), request.date(), MovementType.ADJUSTMENT, request.quantityChange(),
                null, null, null, null, reason, InventoryRules.trimToNull(request.reference()), null, request.requestId());
        StockMovement saved;
        if (request.quantityChange().signum() < 0) {
            if (request.unitCost() != null) {
                throw new InvalidRequestException("A stock reduction is costed from the oldest stock (FIFO); leave the unit cost empty.");
            }
            Balance stock = ledger.balance(material.getId());
            if (request.quantityChange().negate().compareTo(stock.quantity()) > 0) {
                throw new InvalidRequestException("Only " + InventoryRules.quantity(stock.quantity()) + " " + unitName(material)
                        + " of " + material.getName() + " in stock.");
            }
            saved = ledger.drawFifo(material, posting);
        } else {
            // added stock has no purchase price behind it, so the cost must be stated, never guessed
            if (request.unitCost() == null) {
                throw new InvalidRequestException("Enter the unit cost of the stock being added; it becomes a new FIFO cost layer.");
            }
            BigDecimal unitCost = request.unitCost().setScale(FifoCosting.UNIT_COST_SCALE);
            saved = ledger.addLayer(material, posting.priced(unitCost, FifoCosting.receiptCost(request.quantityChange(), unitCost)),
                    CostLayerOrigin.ADJUSTMENT);
        }
        return new MovementResult(toResponse(saved), true);
    }

    @Transactional(readOnly = true)
    public StockMovementResponse get(long id) {
        return toResponse(movements.findById(id).orElseThrow(() -> new NotFoundException("Stock movement not found.")));
    }

    // newest first by default (ledger order), id as the tie-breaker
    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> search(MovementFilter filter, Pageable pageable) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new InvalidRequestException("from must be on or before to.");
        }
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("The page size can be at most " + MAX_PAGE_SIZE + ".");
        }
        for (Sort.Order order : pageable.getSort()) {
            if (!Set.of("movementDate", "id").contains(order.getProperty())) {
                throw new InvalidRequestException("Stock movements can be sorted by movementDate or id.");
            }
        }
        Sort sort = pageable.getSort().isUnsorted() ? Sort.by(Sort.Order.desc("id")) : pageable.getSort();
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by(Sort.Order.desc("id")));
        }
        Page<StockMovement> page = movements.findAll(InventorySpecifications.movements(filter),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort));
        return new PageResponse<>(toResponses(page.getContent()), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    // A material's FIFO layers, oldest (next to be used) first; openOnly hides the used-up ones.
    @Transactional(readOnly = true)
    public List<CostLayerResponse> layers(long materialId, boolean openOnly) {
        if (!materials.existsById(materialId)) {
            throw MaterialService.notFound();
        }
        return layers.findForMaterial(materialId, openOnly).stream().map(mapper::toResponse).toList();
    }

    // The stock part of a project's material cost: issued minus returned, at each movement's own cost
    // (the FIFO cost fixed when it was posted, or the average for movements from before FIFO).
    @Transactional(readOnly = true)
    public ProjectMaterialUsageResponse projectUsage(long projectId) {
        projectService.requireExists(projectId);
        List<ProjectUsageRow> rows = movements.projectUsage(projectId);
        Map<Long, Material> byId = materials.findAllById(rows.stream().map(ProjectUsageRow::getMaterialId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(Material::getId, Function.identity()));
        Map<Long, String> unitNames = units.findNames(byId.values().stream().map(Material::getUnitId).collect(Collectors.toSet()));

        List<ProjectMaterialUsageResponse.Line> lines = new ArrayList<>();
        for (Long materialId : rows.stream().map(ProjectUsageRow::getMaterialId).distinct().sorted().toList()) {
            BigDecimal issuedQty = sum(rows, materialId, MovementType.ISSUED, ProjectUsageRow::getQuantity).negate();
            BigDecimal issuedCost = sum(rows, materialId, MovementType.ISSUED, ProjectUsageRow::getCost).negate();
            BigDecimal returnedQty = sum(rows, materialId, MovementType.RETURNED, ProjectUsageRow::getQuantity);
            BigDecimal returnedCost = sum(rows, materialId, MovementType.RETURNED, ProjectUsageRow::getCost);
            Material m = byId.get(materialId);
            lines.add(new ProjectMaterialUsageResponse.Line(materialId, m.getSku(), m.getName(), unitNames.get(m.getUnitId()),
                    issuedQty, Money.amount(issuedCost), returnedQty, Money.amount(returnedCost),
                    issuedQty.subtract(returnedQty), Money.amount(issuedCost.subtract(returnedCost))));
        }
        BigDecimal issued = Money.amount(lines.stream().map(ProjectMaterialUsageResponse.Line::issuedCost).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal returned = Money.amount(lines.stream().map(ProjectMaterialUsageResponse.Line::returnedCost).reduce(BigDecimal.ZERO, BigDecimal::add));
        return new ProjectMaterialUsageResponse(projectId, lines, issued, returned, Money.amount(issued.subtract(returned)));
    }

    private Material lock(long materialId) {
        // the material is part of the request body, so a missing one is bad input, not a missing URL
        return materials.findByIdForUpdate(materialId)
                .orElseThrow(() -> new InvalidRequestException("The selected material does not exist."));
    }

    private static void requireActive(Material material) {
        if (!material.isActive()) {
            throw new InvalidRequestException(material.getName() + " is inactive and can't take new stock movements.");
        }
    }

    private ProjectResponse project(long projectId) {
        try {
            return projectService.get(projectId);
        } catch (NotFoundException ex) {
            throw new InvalidRequestException("The selected project does not exist.");
        }
    }

    // A resent submission gets the first movement back; the same id reused for something else is a
    // client bug and a 409. Checked after the material lock, so concurrent resends find the first one.
    private Optional<StockMovementResponse> replay(UUID requestId, MovementType type, long materialId,
            BigDecimal quantity, Long projectId) {
        Optional<StockMovement> previous = movements.findByRequestId(requestId);
        if (previous.isEmpty()) {
            return Optional.empty();
        }
        StockMovement m = previous.get();
        boolean same = m.getType() == type && m.getMaterialId() == materialId
                && m.getQuantityChange().abs().compareTo(quantity.abs()) == 0
                && Objects.equals(m.getProjectId(), projectId);
        if (!same) {
            throw new ConflictException("This request id was already used for a different stock movement.");
        }
        return Optional.of(toResponse(m));
    }

    private String unitName(Material material) {
        return units.findNames(List.of(material.getUnitId())).get(material.getUnitId());
    }

    private StockMovementResponse toResponse(StockMovement movement) {
        return toResponses(List.of(movement)).getFirst();
    }

    private List<StockMovementResponse> toResponses(List<StockMovement> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, String> materialNames = materials.findAllById(list.stream().map(StockMovement::getMaterialId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(Material::getId, Material::getName));
        Map<Long, String> supplierNames = supplierService.findNames(
                list.stream().map(StockMovement::getSupplierId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Collection<Long> issueIds = list.stream().filter(m -> m.getType() == MovementType.ISSUED).map(StockMovement::getId).toList();
        Map<Long, BigDecimal> returned = issueIds.isEmpty() ? Map.of() : movements.sumReturnedByIssue(issueIds).stream()
                .collect(Collectors.toMap(IssueTotal::getIssueId, IssueTotal::getTotal));

        List<Long> ids = list.stream().map(StockMovement::getId).toList();
        Map<Long, Long> createdLayer = layers.findBySourceMovementIdIn(ids).stream()
                .filter(l -> l.getOrigin() != CostLayerOrigin.LEGACY_BALANCE) // that layer isn't the movement's own stock
                .collect(Collectors.toMap(CostLayer::getSourceMovementId, CostLayer::getId));
        List<CostAllocation> allAllocations = allocations.findByMovementIdInOrderByIdAsc(ids);
        Map<Long, CostLayer> layerById = allAllocations.isEmpty() ? Map.of() : layers.findAllById(
                allAllocations.stream().map(CostAllocation::getLayerId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(CostLayer::getId, Function.identity()));
        Map<Long, List<CostAllocationResponse>> allocationsByMovement = allAllocations.stream().collect(Collectors.groupingBy(
                CostAllocation::getMovementId, Collectors.mapping(a -> mapper.toResponse(a, layerById.get(a.getLayerId())),
                        Collectors.toList())));

        return list.stream().map(m -> {
            BigDecimal back = null;
            BigDecimal returnable = null;
            if (m.getType() == MovementType.ISSUED) {
                back = returned.getOrDefault(m.getId(), BigDecimal.ZERO.setScale(3));
                returnable = m.getQuantityChange().negate().subtract(back);
            }
            return mapper.toResponse(m, materialNames.get(m.getMaterialId()),
                    m.getSupplierId() == null ? null : supplierNames.get(m.getSupplierId()), back, returnable,
                    createdLayer.get(m.getId()), allocationsByMovement.getOrDefault(m.getId(), List.of()));
        }).toList();
    }

    private static BigDecimal sum(List<ProjectUsageRow> rows, long materialId, MovementType type,
            Function<ProjectUsageRow, BigDecimal> field) {
        return rows.stream().filter(r -> r.getMaterialId() == materialId && r.getType() == type)
                .map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // created is false when an earlier movement with the same requestId was returned instead
    public record MovementResult(StockMovementResponse movement, boolean created) {
    }
}
