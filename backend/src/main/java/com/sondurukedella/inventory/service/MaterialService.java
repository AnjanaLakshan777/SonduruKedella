package com.sondurukedella.inventory.service;

import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.inventory.dto.InventoryValuationResponse;
import com.sondurukedella.inventory.dto.MaterialCreateRequest;
import com.sondurukedella.inventory.dto.MaterialResponse;
import com.sondurukedella.inventory.dto.MaterialStatusRequest;
import com.sondurukedella.inventory.dto.MaterialUpdateRequest;
import com.sondurukedella.inventory.entity.Material;
import com.sondurukedella.inventory.entity.StockMovement;
import com.sondurukedella.inventory.mapper.InventoryMapper;
import com.sondurukedella.inventory.repository.CostLayerRepository;
import com.sondurukedella.inventory.repository.CostLayerRepository.LayerTotal;
import com.sondurukedella.inventory.repository.InventorySpecifications;
import com.sondurukedella.inventory.repository.MaterialRepository;
import com.sondurukedella.inventory.repository.StockMovementRepository;
import com.sondurukedella.settings.service.MeasurementUnitService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The materials catalogue and its balances. Units are checked through MeasurementUnitService.
// Stock is never stored on the material: the quantity is the last ledger row's, the value the sum of
// its open FIFO layers (which every posting checks against the ledger).
@Service
@RequiredArgsConstructor
public class MaterialService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final Set<String> SORTABLE_PROPERTIES = Set.of("sku", "name", "category", "createdAt", "id");
    private static final BigDecimal ZERO_QTY = BigDecimal.ZERO.setScale(3);
    private static final BigDecimal ZERO_VALUE = BigDecimal.ZERO.setScale(Money.SCALE);

    private final MaterialRepository repository;
    private final StockMovementRepository movements;
    private final CostLayerRepository layers;
    private final InventoryMapper mapper;
    private final MeasurementUnitService units;

    @Transactional
    public MaterialResponse create(MaterialCreateRequest request) {
        String sku = request.sku().strip();
        requireUniqueSku(sku, null);
        requireActiveUnit(request.unitId());
        Material material = new Material();
        material.setSku(sku);
        material.setName(request.name().strip());
        material.setCategory(request.category().strip());
        material.setUnitId(request.unitId());
        material.setUnitCost(cost(request.defaultUnitCost()));
        return toResponse(repository.saveAndFlush(material));
    }

    @Transactional
    public MaterialResponse update(long id, MaterialUpdateRequest request) {
        Material material = repository.findByIdForUpdate(id).orElseThrow(MaterialService::notFound);
        ConflictException.requireCurrentVersion(request.version(), material.getVersion());
        String sku = request.sku().strip();
        requireUniqueSku(sku, id);

        if (!request.unitId().equals(material.getUnitId())) {
            // every quantity already in the ledger is in the old unit
            if (movements.existsByMaterialId(id)) {
                throw new InvalidRequestException("The unit can't change once stock has moved; create a new material instead.");
            }
            requireActiveUnit(request.unitId());
            material.setUnitId(request.unitId());
        }
        // only a reference price: the FIFO layers cost the stock, so changing it revalues nothing
        if (request.defaultUnitCost() != null) {
            material.setUnitCost(cost(request.defaultUnitCost()));
        }
        material.setSku(sku);
        material.setName(request.name().strip());
        material.setCategory(request.category().strip());
        return toResponse(repository.saveAndFlush(material));
    }

    @Transactional
    public MaterialResponse setActive(long id, MaterialStatusRequest request) {
        Material material = repository.findByIdForUpdate(id).orElseThrow(MaterialService::notFound);
        ConflictException.requireCurrentVersion(request.version(), material.getVersion());
        if (material.isActive() != request.active()) {
            material.setActive(request.active());
            material = repository.saveAndFlush(material);
        }
        return toResponse(material);
    }

    @Transactional(readOnly = true)
    public MaterialResponse get(long id) {
        return toResponse(repository.findById(id).orElseThrow(MaterialService::notFound));
    }

    @Transactional(readOnly = true)
    public PageResponse<MaterialResponse> search(String query, String category, Boolean active, Pageable pageable) {
        Page<Material> page = repository.findAll(InventorySpecifications.materials(query, category, active), stablePage(pageable));
        List<MaterialResponse> rows = toResponses(page.getContent());
        return new PageResponse<>(rows, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    // the FIFO value of the store: what is left in every open layer
    @Transactional(readOnly = true)
    public InventoryValuationResponse valuation() {
        return new InventoryValuationResponse(repository.count(), layers.countMaterialsInStock(), Money.amount(layers.totalValue()));
    }

    private MaterialResponse toResponse(Material material) {
        return toResponses(List.of(material)).getFirst();
    }

    private List<MaterialResponse> toResponses(List<Material> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(Material::getId).toList();
        Map<Long, StockMovement> latest = movements.findLatestFor(ids).stream()
                .collect(Collectors.toMap(StockMovement::getMaterialId, Function.identity()));
        Map<Long, BigDecimal> values = layers.totalsFor(ids).stream()
                .collect(Collectors.toMap(LayerTotal::getMaterialId, LayerTotal::getValue));
        Map<Long, String> unitNames = units.findNames(list.stream().map(Material::getUnitId).collect(Collectors.toSet()));
        return list.stream().map(m -> {
            StockMovement last = latest.get(m.getId());
            BigDecimal value = values.get(m.getId());
            return mapper.toResponse(m, unitNames.get(m.getUnitId()),
                    last == null ? ZERO_QTY : last.getQuantityAfter(), value == null ? ZERO_VALUE : Money.amount(value));
        }).toList();
    }

    private void requireUniqueSku(String sku, Long self) {
        repository.findBySkuIgnoreCase(sku).filter(m -> !m.getId().equals(self)).ifPresent(m -> {
            throw new ConflictException("SKU \"" + sku + "\" is already used by " + m.getName() + ".");
        });
    }

    private void requireActiveUnit(long unitId) {
        if (!units.isActive(unitId)) {
            throw new InvalidRequestException("Choose a unit from the Settings list.");
        }
    }

    private static BigDecimal cost(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(FifoCosting.UNIT_COST_SCALE, RoundingMode.HALF_UP);
    }

    static NotFoundException notFound() {
        return new NotFoundException("Material not found.");
    }

    // name A-Z by default; id breaks ties
    private static Pageable stablePage(Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("The page size can be at most " + MAX_PAGE_SIZE + ".");
        }
        Sort sort = pageable.getSort();
        for (Sort.Order order : sort) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidRequestException("Materials cannot be sorted by \"" + order.getProperty()
                        + "\". Use one of: sku, name, category, createdAt, id.");
            }
        }
        if (sort.isUnsorted()) {
            sort = Sort.by("name");
        }
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by("id"));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
