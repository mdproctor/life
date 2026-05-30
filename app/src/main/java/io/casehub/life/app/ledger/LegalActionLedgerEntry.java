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
@Table(name = "legal_action_ledger_entry")
@DiscriminatorValue("LEGAL_ACTION")
public class LegalActionLedgerEntry extends LedgerEntry {

    @Column(name = "work_item_id", nullable = false)
    public UUID workItemId;

    @Column(name = "legal_obligation", nullable = false, length = 255)
    public String legalObligation;

    @Column(name = "filing_deadline", nullable = false)
    public Instant filingDeadline;

    @Column(name = "jurisdiction", length = 100)
    public String jurisdiction;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    public LifeDecisionEventType eventType;

    @Column(name = "action_taken", length = 255)
    public String actionTaken;
}
