package io.casehub.life.app.ledger;

import io.casehub.life.app.LifeDecisionEventType;
import io.casehub.ledger.runtime.model.LedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "financial_decision_ledger_entry")
@DiscriminatorValue("FINANCIAL_DECISION")
public class FinancialDecisionLedgerEntry extends LedgerEntry {

    @Column(name = "work_item_id")
    public UUID workItemId;

    @Column(name = "oversight_ref", nullable = false)
    public UUID oversightRef;

    @Column(name = "amount_threshold", nullable = false, precision = 15, scale = 2)
    public BigDecimal amountThreshold;

    @Column(name = "purchase_category", nullable = false, length = 100)
    public String purchaseCategory;

    @Column(name = "approved_by", length = 255)
    public String approvedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    public LifeDecisionEventType eventType;
}
