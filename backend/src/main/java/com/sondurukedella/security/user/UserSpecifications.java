package com.sondurukedella.security.user;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class UserSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private UserSpecifications() {
    }

    static Specification<AppUser> search(String query, UserRole role, Boolean enabled) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + escapeLike(query.strip().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("displayName")), pattern, LIKE_ESCAPE),
                        cb.like(root.<String>get("email"), pattern, LIKE_ESCAPE)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    // so % and _ typed in search are matched literally
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
