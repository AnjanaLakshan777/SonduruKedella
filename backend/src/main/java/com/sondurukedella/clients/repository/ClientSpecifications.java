package com.sondurukedella.clients.repository;

import com.sondurukedella.clients.entity.Client;
import com.sondurukedella.clients.entity.ClientKind;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class ClientSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private ClientSpecifications() {
    }

    // null/blank args are ignored, so search(null, null) returns everyone
    public static Specification<Client> search(String query, ClientKind kind) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (kind != null) {
                predicates.add(cb.equal(root.get("kind"), kind));
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + escapeLike(query.strip().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("city")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("phone")), pattern, LIKE_ESCAPE)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    // otherwise typing "100%" in the search box would match everything
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
