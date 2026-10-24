package com.sondurukedella.projects.service;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;

// PRJ-YYYY-NNN, numbered per start-date year. Past 999 it just grows to 4 digits, which still
// fits the 30-character column.
final class ProjectCodes {

    static final String UNIQUE_CONSTRAINT = "uq_projects_code";
    // first argument of pg_advisory_xact_lock(int, int), so these locks can't clash with others
    static final int LOCK_NAMESPACE = 1_001;

    // capped at 9 digits so the number always fits in an int
    private static final Pattern SUFFIX = Pattern.compile("\\d{1,9}");

    private ProjectCodes() {
    }

    static String prefix(int year) {
        return "PRJ-" + year + "-";
    }

    // Ignores codes that don't follow the pattern instead of failing on them - one odd code
    // typed in by hand mustn't block every new project for that year.
    static String next(int year, Collection<String> existingCodes) {
        String prefix = prefix(year);
        int max = 0;
        for (String code : existingCodes) {
            if (code.startsWith(prefix)) {
                Matcher m = SUFFIX.matcher(code.substring(prefix.length()));
                if (m.matches()) {
                    max = Math.max(max, Integer.parseInt(m.group()));
                }
            }
        }
        return prefix + String.format("%03d", max + 1);
    }

    // Only a clash on the code is worth retrying; any other integrity error is a real problem.
    static boolean isCodeCollision(DataIntegrityViolationException ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve
                    && UNIQUE_CONSTRAINT.equalsIgnoreCase(cve.getConstraintName())) {
                return true;
            }
            if (t.getMessage() != null && t.getMessage().contains(UNIQUE_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }
}
