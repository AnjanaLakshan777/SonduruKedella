package com.sondurukedella.settings.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PaydayTest {

    @Test
    void paydayIsTheFirstChosenDayFromTheWeeksFridayOn() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        // Friday, Saturday, Sunday: same week (Friday and Saturday keep their old dates)
        assertThat(monday.plusDays(Payday.FRIDAY.daysAfterWeekStart())).isEqualTo("2026-09-11");
        assertThat(monday.plusDays(Payday.SATURDAY.daysAfterWeekStart())).isEqualTo("2026-09-12");
        assertThat(monday.plusDays(Payday.SUNDAY.daysAfterWeekStart())).isEqualTo("2026-09-13");
        // Monday to Thursday: the next week, once the work is done
        assertThat(monday.plusDays(Payday.MONDAY.daysAfterWeekStart())).isEqualTo("2026-09-14");
        assertThat(monday.plusDays(Payday.THURSDAY.daysAfterWeekStart())).isEqualTo("2026-09-17");
    }

    @Test
    void everyPaydayLandsOnItsOwnWeekday() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        for (Payday p : Payday.values()) {
            assertThat(monday.plusDays(p.daysAfterWeekStart()).getDayOfWeek()).isEqualTo(DayOfWeek.valueOf(p.name()));
        }
    }
}
