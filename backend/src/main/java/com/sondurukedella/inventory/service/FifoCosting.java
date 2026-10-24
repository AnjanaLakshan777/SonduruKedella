package com.sondurukedella.inventory.service;

import com.sondurukedella.common.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

// FIFO costing arithmetic, kept pure so every rule can be tested on its own (DATABASE.md "Stock costing").
// Money is rounded HALF_UP to 2 decimals, unit costs to 4.
//   receipt / positive adjustment   a new layer: cost = round(q x unit cost, 2)
//   issue / negative adjustment     take from the oldest layers first; each draw is priced with share()
//   return                          put back against the issue's own draws, newest draw first, with share()
// share() never leaves rounding behind: taking the last of a pool takes exactly its value, so an
// emptied layer is worth 0.00 and a fully returned issue gives back exactly what it cost.
public final class FifoCosting {

    public static final int UNIT_COST_SCALE = 4;

    private FifoCosting() {
    }

    public static BigDecimal receiptCost(BigDecimal quantity, BigDecimal unitCost) {
        return Money.amount(quantity.multiply(unitCost));
    }

    // The cost of q out of a pool (a layer, or what is still out on one draw) holding poolQuantity
    // worth poolValue at unitCost: q x unitCost, except that the last of the pool takes all that is
    // left, and nothing can take more than the pool is worth.
    public static BigDecimal share(BigDecimal quantity, BigDecimal poolQuantity, BigDecimal poolValue, BigDecimal unitCost) {
        BigDecimal value = Money.amount(poolValue);
        if (quantity.compareTo(poolQuantity) == 0) {
            return value;
        }
        return Money.amount(quantity.multiply(unitCost)).min(value);
    }

    // Takes quantity from the pools in the order given. Throws if they can't cover it: the callers
    // check stock first, so that would mean the layers and the ledger disagree.
    public static List<Draw> draw(List<Pool> pools, BigDecimal quantity) {
        List<Draw> draws = new ArrayList<>();
        BigDecimal left = quantity;
        for (int i = 0; i < pools.size() && left.signum() > 0; i++) {
            Pool pool = pools.get(i);
            BigDecimal take = left.min(pool.quantity());
            if (take.signum() <= 0) {
                continue;
            }
            draws.add(new Draw(i, take, share(take, pool.quantity(), pool.value(), pool.unitCost())));
            left = left.subtract(take);
        }
        if (left.signum() > 0) {
            throw new IllegalStateException("The cost layers hold less than the quantity asked for (" + left + " short).");
        }
        return draws;
    }

    public static BigDecimal total(List<Draw> draws) {
        return Money.amount(draws.stream().map(Draw::cost).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    // What one unit of a multi-layer movement cost on average, for the ledger's unit_cost column only.
    // The movement's total_cost is the sum of its draws; this is never used to price anything.
    public static BigDecimal effectiveUnitCost(BigDecimal totalCost, BigDecimal quantity) {
        return totalCost.abs().divide(quantity.abs(), UNIT_COST_SCALE, RoundingMode.HALF_UP);
    }

    public record Pool(BigDecimal quantity, BigDecimal value, BigDecimal unitCost) {
    }

    // index into the pools given to draw()
    public record Draw(int index, BigDecimal quantity, BigDecimal cost) {
    }
}
