package io.casehub.life.app.entity;

import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.model.HouseholdTaskStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "household_task")
public class HouseholdTask extends PanacheEntityBase {

    @Id
    public UUID id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public LifeDomain domain;

    @NotNull
    @Column(nullable = false)
    public String title;

    public String description;

    public Instant deadline;

    @Column(name = "sla_hours")
    public Integer slaHours;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public HouseholdTaskStatus status = HouseholdTaskStatus.PENDING;

    @Column(name = "assigned_to")
    public String assignedTo;

    @Column(name = "external_actor_id")
    public UUID externalActorId;

    public String recurrence;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
