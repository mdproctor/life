package io.casehub.life.app.service.ledger;

import io.casehub.life.api.LifeDomain;
import io.casehub.life.app.LifeDecisionEventType;
import io.casehub.life.app.entity.LifeCommitmentRecord;
import io.casehub.work.runtime.model.WorkItem;

import java.util.UUID;

public interface DomainLedgerHandler {
    LifeDomain domain();

    void writeEntry(LifeDecisionEventType event, UUID workItemId, WorkItem workItem);

    default void writeEntry(LifeDecisionEventType event, LifeCommitmentRecord record) {
        // Default no-op: only FinanceDomainLedgerHandler overrides this
    }
}
