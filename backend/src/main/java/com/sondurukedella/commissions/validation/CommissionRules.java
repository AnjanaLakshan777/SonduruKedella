package com.sondurukedella.commissions.validation;

import com.sondurukedella.commissions.entity.CommissionArrangement;
import java.util.Locale;

// Text rules for arrangements: names are trimmed and inner runs of spaces collapsed, so
// "Nimal  Perera " and "Nimal Perera" are one recipient. Comparison also ignores case.
public final class CommissionRules {

    private CommissionRules() {
    }

    public static String cleanName(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }

    public static String cleanRole(String value) {
        String role = cleanName(value);
        return role.isEmpty() ? CommissionArrangement.DEFAULT_ROLE : role;
    }

    // the key the unique index uses: (project, lower(recipient), lower(role))
    public static String key(String recipient, String role) {
        return cleanName(recipient).toLowerCase(Locale.ROOT) + "\u0000" + cleanRole(role).toLowerCase(Locale.ROOT);
    }

    public static String recipientKey(String recipient) {
        return cleanName(recipient).toLowerCase(Locale.ROOT);
    }

    public static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
