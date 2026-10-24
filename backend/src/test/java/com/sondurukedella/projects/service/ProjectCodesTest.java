package com.sondurukedella.projects.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ProjectCodesTest {

    @Test
    void theFirstProjectOfAYearIsNumberOne() {
        assertThat(ProjectCodes.next(2027, List.of())).isEqualTo("PRJ-2027-001");
    }

    @Test
    void numberingContinuesFromTheHighestCodeOfThatYear() {
        assertThat(ProjectCodes.next(2026, List.of("PRJ-2026-001", "PRJ-2026-007", "PRJ-2026-003")))
                .isEqualTo("PRJ-2026-008");
    }

    @Test
    void otherYearsAndOddCodesAreIgnored() {
        // the repository only returns this year's prefix, but don't trust that blindly
        assertThat(ProjectCodes.next(2026, List.of("PRJ-2025-050", "PRJ-2026-old", "LEGACY-9", "PRJ-2026-002")))
                .isEqualTo("PRJ-2026-003");
    }

    @Test
    void aHugeHandEnteredSuffixDoesNotBreakNumbering() {
        // used to throw NumberFormatException, which would block every new project that year
        assertThat(ProjectCodes.next(2026, List.of("PRJ-2026-99999999999", "PRJ-2026-004")))
                .isEqualTo("PRJ-2026-005");
    }

    @Test
    void yearsThatAreNotFourDigitsStillNumberUp() {
        assertThat(ProjectCodes.next(10000, List.of("PRJ-10000-001"))).isEqualTo("PRJ-10000-002");
    }

    @Test
    void numbersKeepGrowingPast999() {
        assertThat(ProjectCodes.next(2026, List.of("PRJ-2026-999"))).isEqualTo("PRJ-2026-1000");
    }

    @Test
    void onlyAClashOnTheProjectCodeCountsAsACollision() {
        assertThat(ProjectCodes.isCodeCollision(violation("uq_projects_code"))).isTrue();
        assertThat(ProjectCodes.isCodeCollision(violation("ck_projects_dates"))).isFalse();
        assertThat(ProjectCodes.isCodeCollision(new DataIntegrityViolationException("something else"))).isFalse();
    }

    private static DataIntegrityViolationException violation(String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("violation", new SQLException("duplicate"), constraint));
    }
}
