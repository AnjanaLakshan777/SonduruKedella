package com.sondurukedella.suppliers.entity;

import com.sondurukedella.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// A supplier in the directory. Never deleted: expenses (and later supplier invoices) keep pointing at
// it, so it is deactivated instead (active, added by V2). No unique key on name or phone - two shops
// can share a name, and branches can share a phone.
@Getter
@Setter
@Entity
@Table(name = "suppliers")
@NoArgsConstructor
public class Supplier extends AuditableEntity {

    public static final int NAME_MAX_LENGTH = 200;

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "contact_person", length = 150)
    private String contactPerson;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(length = 300)
    private String address;

    // what they supply, e.g. "Cement, steel, sand" - shown in the directory
    @Column(length = 300)
    private String supplies;

    @Column(name = "credit_days", nullable = false)
    private int creditDays;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Setter(AccessLevel.NONE)
    private Long version;
}
