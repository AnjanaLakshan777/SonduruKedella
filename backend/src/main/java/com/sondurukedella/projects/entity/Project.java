package com.sondurukedella.projects.entity;

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
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// clientId is a plain id rather than a @ManyToOne to Client - that entity belongs to the
// clients module and we only talk to it through ClientService.
// Code, type, client and contract value have no setters: they're fixed once the project exists.
@Getter
@Entity
@Table(name = "projects")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project extends AuditableEntity {

    public static final int CODE_MAX_LENGTH = 30;
    public static final int NAME_MAX_LENGTH = 200;
    public static final int LOCATION_MAX_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = CODE_MAX_LENGTH, updatable = false)
    private String code;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "client_id", nullable = false, updatable = false)
    private Long clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false, length = 20, updatable = false)
    private ProjectType type;

    @Setter
    @Column(length = LOCATION_MAX_LENGTH)
    private String location;

    @Setter
    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "contract_value", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal contractValue;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "expected_completion", nullable = false)
    private LocalDate expectedCompletion;

    @Setter
    @Column(name = "actual_completion")
    private LocalDate actualCompletion;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status;

    @Setter
    @Column(columnDefinition = "text")
    private String notes;

    @Version
    private Long version;

    public Project(String code, String name, long clientId, ProjectType type, BigDecimal contractValue,
            LocalDate startDate, LocalDate expectedCompletion, ProjectStatus status) {
        this.code = code;
        this.name = name;
        this.clientId = clientId;
        this.type = type;
        this.contractValue = contractValue;
        this.startDate = startDate;
        this.expectedCompletion = expectedCompletion;
        this.status = status;
    }
}
