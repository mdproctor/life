package io.casehub.life.app.entity;

import io.casehub.life.api.LifeActorType;
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
@Table(name = "external_actor")
public class ExternalActor extends PanacheEntityBase {

    @Id
    public UUID id;

    @NotNull
    @Column(nullable = false)
    public String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false)
    public LifeActorType actorType;

    @NotNull
    @Column(name = "contact_method", nullable = false)
    public String contactMethod;

    @NotNull
    @Column(name = "contact_value", nullable = false)
    public String contactValue;

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
