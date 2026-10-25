package com.sondurukedella.commissions.service;

import com.sondurukedella.commissions.dto.CommissionPolicyCheck;
import com.sondurukedella.commissions.dto.CommissionState;
import com.sondurukedella.commissions.dto.DesignSplit;
import com.sondurukedella.commissions.entity.CommissionStatus;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.projects.entity.ProjectType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

// The commission arithmetic, with no database or Spring, so it can be tested on its own.
//
// Base: the project's agreed contract value (fixed once the project exists), not what was billed or
// collected - the rule in DATABASE.md and on the Commissions tab. Each arrangement:
//   amount = round(contractValue × ratePct / 100, 2, HALF_UP)
// Design split: pool = round(contractValue × poolPct / 100, 2, HALF_UP), company = contractValue − pool,
// so the residual cent always goes to the company share and the two add up exactly.
public final class CommissionCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private CommissionCalculator() {
    }

    public static BigDecimal amount(BigDecimal contractValue, BigDecimal ratePct) {
        return contractValue.multiply(ratePct).divide(HUNDRED, Money.SCALE, RoundingMode.HALF_UP);
    }

    // 12.5000 -> 12.5 and 20.0000 -> 20 (never 2E+1, which is how stripTrailingZeros alone prints it)
    public static BigDecimal plain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    public static BigDecimal totalRate(Collection<BigDecimal> rates) {
        return plain(rates.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public static DesignSplit designSplit(BigDecimal contractValue, BigDecimal poolPct, BigDecimal allocated) {
        BigDecimal pool = amount(contractValue, poolPct);
        BigDecimal company = Money.amount(contractValue).subtract(pool);
        BigDecimal allocatedAmount = Money.amount(allocated);
        return new DesignSplit(plain(poolPct), pool, plain(HUNDRED.subtract(poolPct)), company,
                allocatedAmount, pool.subtract(allocatedAmount));
    }

    // Design: against the commission pool. Construction: against the optional warning level; no
    // rate is assumed when none is set. A warning only - nothing here refuses anything.
    public static CommissionPolicyCheck policy(ProjectType type, BigDecimal totalRate, BigDecimal designPoolPct,
            BigDecimal constructionWarningPct) {
        BigDecimal total = plain(totalRate);
        BigDecimal limit = type == ProjectType.DESIGN ? designPoolPct : constructionWarningPct;
        if (limit == null) {
            return new CommissionPolicyCheck(null, total, false, null);
        }
        limit = plain(limit);
        boolean exceeded = total.compareTo(limit) > 0;
        String warning = null;
        if (exceeded) {
            warning = type == ProjectType.DESIGN
                    ? "Shares total " + total.toPlainString() + "%, above the " + limit.toPlainString()
                            + "% design commission pool (company share " + HUNDRED.subtract(limit).toPlainString() + "%)."
                    : "Shares total " + total.toPlainString() + "%, above the configured " + limit.toPlainString()
                            + "% construction level.";
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

    // only a confirmed arrangement is owed anything
    public static BigDecimal outstanding(CommissionStatus status, BigDecimal amount, BigDecimal paid) {
        if (status != CommissionStatus.CONFIRMED) {
            return Money.amount(BigDecimal.ZERO);
        }
        return Money.amount(amount.subtract(paid).max(BigDecimal.ZERO));
    }
}
