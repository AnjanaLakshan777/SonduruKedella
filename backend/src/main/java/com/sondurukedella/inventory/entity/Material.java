package com.sondurukedella.inventory.entity;

import com.sondurukedella.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// A material in the company store. unitCost (materials.unit_cost) is the catalogue's default unit
// cost: a reference price typed in by the user, offered as the starting price on a receipt. It plays
// no part in costing - FIFO prices every movement from the cost layers. (Before V4 the ledger kept
// it at the weighted average; rows from then still hold that last average.) unitId points at
// Settings' units. Never deleted - the movement ledger references it - so it is deactivated instead
// (active, added by V3).
@Getter
@Setter
@Entity
@Table(name = "materials")
@NoArgsConstructor
public class Material extends AuditableEntity {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // unique ignoring case (uq_materials_sku)
    @Column(nullable = false, length = 50)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 4)
    private BigDecimal unitCost;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Setter(AccessLevel.NONE)
    private Long version;
}
