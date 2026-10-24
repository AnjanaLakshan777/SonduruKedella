package com.sondurukedella.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sondurukedella.inventory.service.FifoCosting.Draw;
import com.sondurukedella.inventory.service.FifoCosting.Pool;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class FifoCostingTest {

    @Test
    void aReceiptCostsQuantityTimesPriceRoundedToCents() {
        assertThat(FifoCosting.receiptCost(dec("100"), dec("2500"))).isEqualByComparingTo("250000.00");
        // 12.345 x 101.3333 = 1250.9596... -> 1250.96
        assertThat(FifoCosting.receiptCost(dec("12.345"), dec("101.3333"))).isEqualByComparingTo("1250.96");
    }

    @Test
    void theOwnersExampleTakesTheOldLayerFirstThenTheNewOne() {
        // A: 10 at 2,000; B: 10 at 2,400; issue 12 -> 10 x 2,000 + 2 x 2,400 = 24,800, not an average
        List<Draw> draws = FifoCosting.draw(List.of(pool("10", "20000.00", "2000"), pool("10", "24000.00", "2400")), dec("12"));

        assertThat(draws).extracting(Draw::index).containsExactly(0, 1);
        assertThat(draws.get(0).quantity()).isEqualByComparingTo("10");
        assertThat(draws.get(0).cost()).isEqualByComparingTo("20000.00");
        assertThat(draws.get(1).quantity()).isEqualByComparingTo("2");
        assertThat(draws.get(1).cost()).isEqualByComparingTo("4800.00");
        assertThat(FifoCosting.total(draws)).isEqualByComparingTo("24800.00");
        // what is left of B: 8 worth 19,200
        assertThat(dec("24000.00").subtract(draws.get(1).cost())).isEqualByComparingTo("19200.00");
    }

    @Test
    void aSmallIssueTakesOnlyPartOfTheOldestLayer() {
        List<Draw> draws = FifoCosting.draw(List.of(pool("10", "20000.00", "2000"), pool("10", "24000.00", "2400")), dec("3"));
        assertThat(draws).hasSize(1);
        assertThat(draws.getFirst().cost()).isEqualByComparingTo("6000.00");
    }

    @Test
    void usedUpLayersAreSkipped() {
        List<Draw> draws = FifoCosting.draw(List.of(pool("0", "0.00", "2000"), pool("5", "12000.00", "2400")), dec("5"));
        assertThat(draws).extracting(Draw::index).containsExactly(1);
    }

    @Test
    void theLastOfALayerTakesExactlyWhatIsLeftOfItsValue() {
        // 3 units worth 100.00 at 33.3333: 1 + 1 + 1 is 33.33 + 33.33 + 33.34, never a cent more or less
        BigDecimal first = FifoCosting.share(dec("1"), dec("3"), dec("100.00"), dec("33.3333"));
        BigDecimal second = FifoCosting.share(dec("1"), dec("2"), dec("100.00").subtract(first), dec("33.3333"));
        BigDecimal last = FifoCosting.share(dec("1"), dec("1"), dec("100.00").subtract(first).subtract(second), dec("33.3333"));
        assertThat(first).isEqualByComparingTo("33.33");
        assertThat(second).isEqualByComparingTo("33.33");
        assertThat(last).isEqualByComparingTo("33.34");
    }

    @Test
    void aDrawCanNeverTakeMoreThanTheLayerIsWorth() {
        // the rounded unit cost would overshoot what is left; the cap holds it to the layer's value
        assertThat(FifoCosting.share(dec("2"), dec("3"), dec("0.01"), dec("0.0100"))).isEqualByComparingTo("0.01");
    }

    @Test
    void layersThatCanNotCoverTheIssueAreABug() {
        assertThatThrownBy(() -> FifoCosting.draw(List.of(pool("5", "100.00", "20")), dec("6")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theLedgerUnitCostOfAMultiLayerIssueIsOnlyForDisplay() {
        // 24,800 / 12 = 2,066.6667: shown on the movement, never used to price anything
        assertThat(FifoCosting.effectiveUnitCost(dec("-24800.00"), dec("-12"))).isEqualByComparingTo("2066.6667");
    }

    private static Pool pool(String quantity, String value, String unitCost) {
        return new Pool(dec(quantity), dec(value), dec(unitCost));
    }

    private static BigDecimal dec(String value) {
        return new BigDecimal(value);
    }
}
