package com.sondurukedella.settings.entity;

import java.time.DayOfWeek;

/**
 * Weekly payday. The payroll week itself always runs Monday to Sunday.
 * Payday is the first such weekday from the week's Friday on, so a week is never paid before most
 * of its work is done: Friday, Saturday and Sunday fall in the same week, Monday to Thursday in the next.
 */
public enum Payday {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY;

    // Friday 4, Saturday 5, Sunday 6, Monday 7 ... Thursday 10
    public int daysAfterWeekStart() {
        int fromFriday = Math.floorMod(DayOfWeek.valueOf(name()).getValue() - DayOfWeek.FRIDAY.getValue(), 7);
        return 4 + fromFriday;
    }
}
