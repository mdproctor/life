package io.casehub.life.app.service.ledger;

import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.app.LifeDecisionEventType;
import io.casehub.life.app.entity.LifeCommitmentRecord;
import io.casehub.work.runtime.model.WorkItem;

import java.util.UUID;

public interface DomainLedgerHandler {
    LifeDomain domain();

    void writeEntry(LifeDecisionEventType event, UUID workItemId, WorkItem workItem);

    default void writeEntry(LifeDecisionEventType event, LifeCommitmentRecord record) {
        // Commitment-based events; handlers that operate purely on WorkItem context may leave this as a no-op.
    }

    static int nextSequenceNumber(LedgerEntryRepository repo, UUID subjectId) {
        return repo.findLatestBySubjectId(subjectId)
                   .map(e -> e.sequenceNumber + 1)
                   .orElse(1);
    }
}
