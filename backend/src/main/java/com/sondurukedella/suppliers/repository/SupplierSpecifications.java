package com.sondurukedella.suppliers.repository;

import com.sondurukedella.suppliers.entity.Supplier;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class SupplierSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private SupplierSpecifications() {
    }

    // q matches name, contact person, phone or what they supply; active null means both.
    public static Specification<Supplier> search(String query, Boolean active) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + escapeLike(query.strip().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("contactPerson")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("phone")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("supplies")), pattern, LIKE_ESCAPE)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    // so "%" or "_" typed in the search box are matched literally
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
