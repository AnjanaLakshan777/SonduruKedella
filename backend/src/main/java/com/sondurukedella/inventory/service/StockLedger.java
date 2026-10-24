package com.sondurukedella.inventory.service;

import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.inventory.entity.CostAllocation;
import com.sondurukedella.inventory.entity.CostLayer;
import com.sondurukedella.inventory.entity.CostLayerOrigin;
import com.sondurukedella.inventory.entity.Material;
import com.sondurukedella.inventory.entity.StockMovement;
import com.sondurukedella.inventory.entity.StockMovement.Posting;
import com.sondurukedella.inventory.repository.CostAllocationRepository;
import com.sondurukedella.inventory.repository.CostAllocationRepository.ReversedTotal;
import com.sondurukedella.inventory.repository.CostLayerRepository;
import com.sondurukedella.inventory.repository.StockMovementRepository;
import com.sondurukedella.inventory.service.FifoCosting.Draw;
import com.sondurukedella.inventory.service.FifoCosting.Pool;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Posts to the stock ledger and keeps the FIFO layers in step with it. Callers must already hold the
// material row lock. Everything runs in the caller's transaction: the movement, its layer or its
// allocations, and the layer balances are saved together or not at all. After every posting the
// open layers must add up to the ledger's running balance exactly, or the whole posting is undone.
@Component
@RequiredArgsConstructor
class StockLedger {

    private final StockMovementRepository movements;
    private final CostLayerRepository layers;
    private final CostAllocationRepository allocations;

    Balance balance(long materialId) {
        return movements.findFirstByMaterialIdOrderByIdDesc(materialId)
                .map(m -> new Balance(m.getQuantityAfter(), m.getValueAfter()))
                .orElse(new Balance(BigDecimal.ZERO.setScale(3), BigDecimal.ZERO.setScale(2)));
    }

    // receipts, positive adjustments and returns against pre-FIFO issues: the movement becomes a new
    // layer at its own unit cost, queued behind every older one
    StockMovement addLayer(Material locked, Posting posting, CostLayerOrigin origin) {
        StockMovement saved = append(locked, posting);
        layers.saveAndFlush(CostLayer.of(saved, origin));
        reconcile(locked, saved);
        return saved;
    }

    // issues and negative adjustments: oldest layers first, each draw kept as an allocation
    StockMovement drawFifo(Material locked, Posting unpriced) {
        BigDecimal quantity = unpriced.quantityChange().negate();
        List<CostLayer> open = layers.findOpenForUpdate(locked.getId());
        List<Draw> draws = FifoCosting.draw(open.stream()
                .map(l -> new Pool(l.getRemainingQuantity(), l.getRemainingValue(), l.getUnitCost())).toList(), quantity);
        BigDecimal cost = FifoCosting.total(draws);
        StockMovement saved = append(locked, unpriced.priced(FifoCosting.effectiveUnitCost(cost, quantity), cost.negate()));

        List<CostAllocation> rows = new ArrayList<>();
        for (Draw d : draws) {
            CostLayer layer = open.get(d.index());
            layer.take(d.quantity(), d.cost());
            rows.add(CostAllocation.draw(saved, layer, d.quantity(), d.cost()));
        }
        layers.saveAllAndFlush(open);
        allocations.saveAllAndFlush(rows);
        reconcile(locked, saved);
        return saved;
    }

    // A return against a FIFO issue puts the stock back into the very layers it came from, at their
    // cost. The issue's draws are reversed newest first (undoing the issue from its end), and the last
    // of each draw takes exactly its remaining cost, so a full return reverses the issue to the cent.
    StockMovement returnToLayers(Material locked, StockMovement issue, Posting unpriced) {
        List<CostAllocation> taken = new ArrayList<>(allocations.findByMovementIdOrderByIdAsc(issue.getId()));
        Collections.reverse(taken);
        Map<Long, ReversedTotal> back = allocations.reversedTotals(taken.stream().map(CostAllocation::getId).toList())
                .stream().collect(Collectors.toMap(ReversedTotal::getDrawId, Function.identity()));
        Map<Long, CostLayer> layerById = layers.findAllForUpdate(taken.stream().map(CostAllocation::getLayerId).distinct().toList())
                .stream().collect(Collectors.toMap(CostLayer::getId, Function.identity()));

        // what is still out on each draw
        List<Pool> pools = taken.stream().map(a -> {
            ReversedTotal r = back.get(a.getId());
            BigDecimal qty = a.getQuantity().negate().subtract(r == null ? BigDecimal.ZERO : r.getQuantity());
            BigDecimal value = a.getCost().negate().subtract(r == null ? BigDecimal.ZERO : r.getCost());
            return new Pool(qty, value, layerById.get(a.getLayerId()).getUnitCost());
        }).toList();
        BigDecimal quantity = unpriced.quantityChange();
        List<Draw> draws = FifoCosting.draw(pools, quantity);
        BigDecimal cost = FifoCosting.total(draws);
        StockMovement saved = append(locked, unpriced.priced(FifoCosting.effectiveUnitCost(cost, quantity), cost));

        List<CostAllocation> rows = new ArrayList<>();
        for (Draw d : draws) {
            CostAllocation original = taken.get(d.index());
            CostLayer layer = layerById.get(original.getLayerId());
            layer.putBack(d.quantity(), d.cost());
            rows.add(CostAllocation.reversal(saved, layer, d.quantity(), d.cost(), original.getId()));
        }
        layers.saveAllAndFlush(layerById.values());
        allocations.saveAllAndFlush(rows);
        reconcile(locked, saved);
        return saved;
    }

    private StockMovement append(Material locked, Posting posting) {
        Balance before = balance(locked.getId());
        BigDecimal quantityAfter = before.quantity().add(posting.quantityChange());
        BigDecimal valueAfter = before.value().add(posting.totalCost());
        // the services check this first with a friendlier message; this is the backstop
        if (quantityAfter.signum() < 0 || valueAfter.signum() < 0) {
            throw new InvalidRequestException("Not enough stock of " + locked.getName() + ".");
        }
        return movements.saveAndFlush(StockMovement.post(posting, quantityAfter, valueAfter));
    }

    // The ledger's running balance and the layers are two views of the same stock. If they ever drift
    // apart it is a bug, and refusing the posting (rolling it back) is safer than carrying it forward.
    private void reconcile(Material locked, StockMovement posted) {
        var totals = layers.totalsFor(List.of(locked.getId()));
        BigDecimal quantity = totals.isEmpty() ? BigDecimal.ZERO : totals.getFirst().getQuantity();
        BigDecimal value = totals.isEmpty() ? BigDecimal.ZERO : totals.getFirst().getValue();
        if (quantity.compareTo(posted.getQuantityAfter()) != 0 || value.compareTo(posted.getValueAfter()) != 0) {
            throw new IllegalStateException("Cost layers for material " + locked.getId() + " hold " + quantity + " worth "
                    + value + ", but the ledger says " + posted.getQuantityAfter() + " worth " + posted.getValueAfter() + ".");
        }
    }

    record Balance(BigDecimal quantity, BigDecimal value) {
    }
}
