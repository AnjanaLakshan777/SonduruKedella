package com.sondurukedella.projects.repository;

import com.sondurukedella.projects.dto.ProjectFilter;
import com.sondurukedella.projects.entity.Project;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class ProjectSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private ProjectSpecifications() {
    }

    // Client names live in the clients module, so the caller looks up the matching client ids
    // first and passes them in, instead of us joining to the clients table.
    public static Specification<Project> matching(ProjectFilter filter, Collection<Long> clientIdsMatchingQuery) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.type() != null) {
                predicates.add(cb.equal(root.get("type"), filter.type()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.clientId() != null) {
                predicates.add(cb.equal(root.get("clientId"), filter.clientId()));
            }
            if (filter.startFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startDate"), filter.startFrom()));
            }
            if (filter.startTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startDate"), filter.startTo()));
            }
            if (filter.dueFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("expectedCompletion"), filter.dueFrom()));
            }
            if (filter.dueTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("expectedCompletion"), filter.dueTo()));
            }
            if (filter.q() != null && !filter.q().isBlank()) {
                String pattern = "%" + escapeLike(filter.q().strip().toLowerCase(Locale.ROOT)) + "%";
                List<Predicate> text = new ArrayList<>(List.of(
                        cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("code")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("location")), pattern, LIKE_ESCAPE)));
                if (!clientIdsMatchingQuery.isEmpty()) {
                    text.add(root.get("clientId").in(clientIdsMatchingQuery));
                }
                predicates.add(cb.or(text.toArray(Predicate[]::new)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    // so "%" or "_" typed in the search box are matched literally
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
