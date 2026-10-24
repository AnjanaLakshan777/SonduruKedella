package com.sondurukedella.inventory.repository;

import com.sondurukedella.inventory.dto.MovementFilter;
import com.sondurukedella.inventory.entity.Material;
import com.sondurukedella.inventory.entity.StockMovement;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class InventorySpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private InventorySpecifications() {
    }

    // q matches SKU, name or category; category is an exact (case-insensitive) match; active null = both
    public static Specification<Material> materials(String query, String category, Boolean active) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.strip().toLowerCase(Locale.ROOT)));
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + escapeLike(query.strip().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("sku")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("category")), pattern, LIKE_ESCAPE)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<StockMovement> movements(MovementFilter filter) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.materialId() != null) {
                predicates.add(cb.equal(root.get("materialId"), filter.materialId()));
            }
            if (filter.type() != null) {
                predicates.add(cb.equal(root.get("type"), filter.type()));
            }
            if (filter.projectId() != null) {
                predicates.add(cb.equal(root.get("projectId"), filter.projectId()));
            }
            if (filter.supplierId() != null) {
                predicates.add(cb.equal(root.get("supplierId"), filter.supplierId()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("movementDate"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("movementDate"), filter.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    // so "%" or "_" typed in the search box are matched literally
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
