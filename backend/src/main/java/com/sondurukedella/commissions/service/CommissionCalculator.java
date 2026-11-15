package com.sondurukedella.commissions.service;

import com.sondurukedella.commissions.dto.CommissionPolicyCheck;
import com.sondurukedella.commissions.dto.CommissionSplit;
import com.sondurukedella.commissions.dto.CommissionState;
import com.sondurukedella.commissions.entity.CommissionStatus;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.projects.entity.ProjectType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

// Pure maths, no Spring, easy to unit test.
// Base is the agreed contract value (not billed/collected). Amounts round half-up to the cent.
// Company share = contract value - pool, so any leftover cent goes to the company.
public final class CommissionCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private CommissionCalculator() {
    }

    public static BigDecimal amount(BigDecimal contractValue, BigDecimal ratePct) {
        return contractValue.multiply(ratePct).divide(HUNDRED, Money.SCALE, RoundingMode.HALF_UP);
    }

    // 20.0000 -> 20, not 2E+1 like stripTrailingZeros on its own
    public static BigDecimal plain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    public static BigDecimal totalRate(Collection<BigDecimal> rates) {
        return plain(rates.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public static CommissionSplit split(BigDecimal contractValue, BigDecimal poolPct, BigDecimal allocated) {
        BigDecimal pool = amount(contractValue, poolPct);
        BigDecimal company = Money.amount(contractValue).subtract(pool);
        BigDecimal allocatedAmount = Money.amount(allocated);
        return new CommissionSplit(plain(poolPct), pool, plain(HUNDRED.subtract(poolPct)), company,
                allocatedAmount, pool.subtract(allocatedAmount));
    }

    // only a warning, never blocks anything. No pool (consultation) = no check.
    public static CommissionPolicyCheck policy(ProjectType type, BigDecimal totalRate, BigDecimal poolPct) {
        BigDecimal total = plain(totalRate);
        BigDecimal limit = poolPct;
        if (limit == null) {
            return new CommissionPolicyCheck(null, total, false, null);
        }
        limit = plain(limit);
        boolean exceeded = total.compareTo(limit) > 0;
        String warning = null;
        if (exceeded) {
            warning = "Shares total " + total.toPlainString() + "%, above the " + limit.toPlainString() + "% "
                    + (type == ProjectType.DESIGN ? "design" : "construction") + " commission pool (company share "
                    + HUNDRED.subtract(limit).toPlainString() + "%).";
        }
        return new CommissionPolicyCheck(limit, total, exceeded, warning);
    }

    public static CommissionState state(CommissionStatus status, BigDecimal amount, BigDecimal paid) {
        return switch (status) {
            case PENDING -> CommissionState.PENDING;
            case VOIDED -> CommissionState.VOIDED;
            case CONFIRMED -> paid.signum() == 0 ? CommissionState.CONFIRMED
                    : paid.compareTo(amount) >= 0 ? CommissionState.PAID : CommissionState.PARTIALLY_PAID;
        };
    }

    public static BigDecimal outstanding(CommissionStatus status, BigDecimal amount, BigDecimal paid) {
        if (status != CommissionStatus.CONFIRMED) {
            return Money.amount(BigDecimal.ZERO);
        }
        return Money.amount(amount.subtract(paid).max(BigDecimal.ZERO));
    }
}
