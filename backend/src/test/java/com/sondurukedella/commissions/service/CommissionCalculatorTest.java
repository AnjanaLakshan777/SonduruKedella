package com.sondurukedella.commissions.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sondurukedella.commissions.dto.CommissionPolicyCheck;
import com.sondurukedella.commissions.dto.CommissionState;
import com.sondurukedella.commissions.dto.DesignSplit;
import com.sondurukedella.commissions.entity.CommissionStatus;
import com.sondurukedella.projects.entity.ProjectType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommissionCalculatorTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void theDocumentedDesignExample() {
        // DATABASE.md: 1,500,000 with 12% and 8% gives 180,000 and 120,000
        assertThat(CommissionCalculator.amount(bd("1500000"), bd("12"))).isEqualByComparingTo("180000.00");
        assertThat(CommissionCalculator.amount(bd("1500000"), bd("8"))).isEqualByComparingTo("120000.00");
        DesignSplit split = CommissionCalculator.designSplit(bd("1500000"), bd("20"), bd("300000"));
        assertThat(split.poolAmount()).isEqualByComparingTo("300000.00");
        assertThat(split.companyShareAmount()).isEqualByComparingTo("1200000.00");
        assertThat(split.companySharePct()).isEqualByComparingTo("80");
        assertThat(split.unallocatedAmount()).isEqualByComparingTo("0");
    }

    @Test
    void amountsAreRoundedHalfUpToTheCent() {
        assertThat(CommissionCalculator.amount(bd("333333.33"), bd("12.5"))).isEqualTo(bd("41666.67")); // 41,666.66625
        assertThat(CommissionCalculator.amount(bd("100000.10"), bd("0.0005"))).isEqualTo(bd("0.50")); // 0.500000500
        assertThat(CommissionCalculator.amount(bd("1"), bd("0.5"))).isEqualTo(bd("0.01")); // 0.005 rounds up
        assertThat(CommissionCalculator.amount(bd("1"), bd("0.4"))).isEqualTo(bd("0.00"));
    }

    @Test
    void companyShareAndPoolAlwaysAddUpToTheContractValueExactly() {
        for (String value : List.of("333333.33", "100000.01", "999999.99", "1.00", "123456.78", "7")) {
            for (String pool : List.of("20", "18.5", "33.3333", "0.0001")) {
                DesignSplit split = CommissionCalculator.designSplit(bd(value), bd(pool), BigDecimal.ZERO);
                assertThat(split.poolAmount().add(split.companyShareAmount())).as(value + " @ " + pool).isEqualByComparingTo(value);
                assertThat(split.poolAmount().scale()).isEqualTo(2);
                assertThat(split.companyShareAmount().scale()).isEqualTo(2);
            }
        }
        // the residual cent goes to the company: 333,333.33 × 20% = 66,666.666 -> 66,666.67, company 266,666.66
        DesignSplit split = CommissionCalculator.designSplit(bd("333333.33"), bd("20"), BigDecimal.ZERO);
        assertThat(split.poolAmount()).isEqualTo(bd("66666.67"));
        assertThat(split.companyShareAmount()).isEqualTo(bd("266666.66"));
    }

    @Test
    void designRatesAreCheckedAgainstThePool() {
        CommissionPolicyCheck ok = CommissionCalculator.policy(ProjectType.DESIGN, bd("20.0000"), bd("20"), null);
        assertThat(ok.exceeded()).isFalse();
        assertThat(ok.warning()).isNull();
        CommissionPolicyCheck over = CommissionCalculator.policy(ProjectType.DESIGN, bd("22"), bd("20"), bd("5"));
        assertThat(over.exceeded()).isTrue();
        assertThat(over.limitPct()).isEqualByComparingTo("20");
        assertThat(over.warning()).contains("22%").contains("20%").contains("company share 80%");
    }

    @Test
    void constructionHasNoRateUnlessALevelIsConfigured() {
        // the design pool never applies to construction
        assertThat(CommissionCalculator.policy(ProjectType.CONSTRUCTION, bd("35"), bd("20"), null).exceeded()).isFalse();
        assertThat(CommissionCalculator.policy(ProjectType.CONSTRUCTION, bd("35"), bd("20"), null).limitPct()).isNull();
        CommissionPolicyCheck over = CommissionCalculator.policy(ProjectType.CONSTRUCTION, bd("6"), bd("20"), bd("5"));
        assertThat(over.exceeded()).isTrue();
        assertThat(over.warning()).contains("construction");
    }

    @Test
    void theShownStatusFollowsThePayments() {
        BigDecimal amount = bd("1000.00");
        assertThat(CommissionCalculator.state(CommissionStatus.PENDING, amount, BigDecimal.ZERO)).isEqualTo(CommissionState.PENDING);
        assertThat(CommissionCalculator.state(CommissionStatus.CONFIRMED, amount, BigDecimal.ZERO)).isEqualTo(CommissionState.CONFIRMED);
        assertThat(CommissionCalculator.state(CommissionStatus.CONFIRMED, amount, bd("0.01"))).isEqualTo(CommissionState.PARTIALLY_PAID);
        assertThat(CommissionCalculator.state(CommissionStatus.CONFIRMED, amount, bd("1000"))).isEqualTo(CommissionState.PAID);
        assertThat(CommissionCalculator.state(CommissionStatus.VOIDED, amount, BigDecimal.ZERO)).isEqualTo(CommissionState.VOIDED);
        assertThat(CommissionCalculator.outstanding(CommissionStatus.CONFIRMED, amount, bd("400"))).isEqualTo(bd("600.00"));
        assertThat(CommissionCalculator.outstanding(CommissionStatus.PENDING, amount, BigDecimal.ZERO)).isEqualTo(bd("0.00"));
        assertThat(CommissionCalculator.outstanding(CommissionStatus.VOIDED, amount, BigDecimal.ZERO)).isEqualTo(bd("0.00"));
    }

    @Test
    void totalRateAddsExactly() {
        assertThat(CommissionCalculator.totalRate(List.of(bd("12.5"), bd("7.25"), bd("0.2500")))).isEqualByComparingTo("20");
    }
}
