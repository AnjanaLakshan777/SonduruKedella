package com.sondurukedella.clients.entity;

import com.sondurukedella.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// No unique key on clients in V1 - two people can have the same name or share a phone.
// There's no archive flag either, and we never delete clients since projects point to them.
@Getter
@Setter
@Entity
@Table(name = "clients")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Client extends AuditableEntity {

    public static final int NAME_MAX_LENGTH = 200;
    public static final int PHONE_MAX_LENGTH = 30;
    public static final int EMAIL_MAX_LENGTH = 150;
    public static final int ADDRESS_MAX_LENGTH = 300;
    public static final int CITY_MAX_LENGTH = 100;

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_type", nullable = false, length = 20)
    private ClientKind kind;

    @Column(nullable = false, length = PHONE_MAX_LENGTH)
    private String phone;

    @Column(length = EMAIL_MAX_LENGTH)
    private String email;

    @Column(length = ADDRESS_MAX_LENGTH)
    private String address;

    @Column(length = CITY_MAX_LENGTH)
    private String city;

    @Column(columnDefinition = "text")
    private String notes;

    @Version
    @Setter(AccessLevel.NONE)
    private Long version;

    public Client(String name, ClientKind kind, String phone) {
        this.name = name;
        this.kind = kind;
        this.phone = phone;
    }
}
