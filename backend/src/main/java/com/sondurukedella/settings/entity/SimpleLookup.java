package com.sondurukedella.settings.entity;

import com.sondurukedella.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An editable Settings list entry (units, payment methods). Removed entries are deactivated, never deleted. */
@Getter
@Setter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class SimpleLookup extends AuditableEntity {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Version
    @Setter(AccessLevel.NONE)
    private Long version;

    protected SimpleLookup(String name, int sortOrder) {
        this.name = name;
        this.sortOrder = sortOrder;
    }
}
