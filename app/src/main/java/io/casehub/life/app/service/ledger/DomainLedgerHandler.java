package io.casehub.life.app.service.ledger;

import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.app.LifeDecisionEventType;
import io.casehub.life.app.entity.LifeCommitmentRecord;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.work.runtime.model.WorkItem;

import java.util.UUID;

public interface DomainLedgerHandler {
    LifeDomain domain();

    // FINANCE: CREATED on this path is an explicit no-op — FINANCE CREATED entries are written
    // via the commitment-based overload from OversightGateStrategy, not from task creation.
    void writeEntry(LifeDecisionEventType event, UUID workItemId, WorkItem workItem);

    default void writeEntry(LifeDecisionEventType event, LifeCommitmentRecord record) {
        // Commitment-based events; handlers that operate purely on WorkItem context may leave this as a no-op.
    }

    static int nextSequenceNumber(LedgerEntryRepository repo, UUID subjectId) {
        return repo.findLatestBySubjectId(subjectId, TenancyConstants.DEFAULT_TENANT_ID)
                   .map(e -> e.sequenceNumber + 1)
                   .orElse(1);
    }
}
