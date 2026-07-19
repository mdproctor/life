package io.casehub.life.app.entity;

import io.casehub.life.api.LifeCaseStatus;
import io.casehub.life.api.LifeDomain;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "life_case_tracker")
public class LifeCaseTracker extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "case_type", nullable = false, length = 64)
    public String     caseType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    public LifeDomain domain;


    @Column(name = "engine_case_id", unique = true)
    public UUID engineCaseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public LifeCaseStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "completed_at")
    public Instant completedAt;

    public static Optional<LifeCaseTracker> findByEngineCaseId(UUID engineCaseId) {
        return find("engineCaseId", engineCaseId).firstResultOptional();
    }

    public static List<LifeCaseTracker> findActiveByCaseType(String caseType) {
        return list("caseType = ?1 and status = ?2", caseType, LifeCaseStatus.ACTIVE);
    }

    public static List<LifeCaseTracker> findByDomain(LifeDomain domain) {
        return list("domain", domain);
    }

    @PrePersist
    void onPersist() {
        if (id == null) {id = UUID.randomUUID();}
        if (createdAt == null) {createdAt = Instant.now();}
        if (status == null) {status = LifeCaseStatus.ACTIVE;}
    }

}
