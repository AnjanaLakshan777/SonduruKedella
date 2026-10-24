package com.sondurukedella.employees.repository;

import com.sondurukedella.employees.entity.Employee;
import com.sondurukedella.employees.entity.EmployeeStatus;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class EmployeeSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private EmployeeSpecifications() {
    }

    // q matches name, role or phone; role is an exact match ignoring case; status null means both
    public static Specification<Employee> search(String query, String role, EmployeeStatus status) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (role != null && !role.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("role")), role.strip().toLowerCase(Locale.ROOT)));
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + escapeLike(query.strip().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("role")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("phone")), pattern, LIKE_ESCAPE)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    // so "%" or "_" typed in the search box are matched literally
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
