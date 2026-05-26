package io.casehub.life.app.entity;

import io.casehub.life.api.LifeDomain;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "life_event")
public class LifeEvent extends PanacheEntityBase {

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

    @NotNull
    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }
}
