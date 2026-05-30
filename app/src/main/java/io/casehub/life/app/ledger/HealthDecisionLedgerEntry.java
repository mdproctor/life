package io.casehub.life.app.ledger;

import io.casehub.life.app.LifeDecisionEventType;
import io.casehub.ledger.runtime.model.LedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "health_decision_ledger_entry")
@DiscriminatorValue("HEALTH_DECISION")
public class HealthDecisionLedgerEntry extends LedgerEntry {

    @Column(name = "work_item_id", nullable = false)
    public UUID workItemId;

    @Column(name = "provider_id")
    public UUID providerId;

    @Column(name = "appointment_date")
    public Instant appointmentDate;

    @Column(name = "task_category", nullable = false, length = 100)
    public String taskCategory;

    @Column(name = "sla_deadline", nullable = false)
    public Instant slaDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    public LifeDecisionEventType eventType;

    @Column(name = "outcome", length = 255)
    public String outcome;
}
