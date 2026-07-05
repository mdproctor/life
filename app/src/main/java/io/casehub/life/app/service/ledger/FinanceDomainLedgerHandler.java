package io.casehub.life.app.service.ledger;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.app.LifeDecisionEventType;
import io.casehub.life.app.entity.LifeCommitmentRecord;
import io.casehub.life.app.ledger.FinancialDecisionLedgerEntry;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.work.runtime.model.WorkItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class FinanceDomainLedgerHandler implements DomainLedgerHandler {

    @Inject LedgerEntryRepository ledgerRepository;

    // Package-visible constructor for testing with injected deps
    FinanceDomainLedgerHandler(LedgerEntryRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    FinanceDomainLedgerHandler() {}

    @Override public LifeDomain domain() { return LifeDomain.FINANCE; }

    @Override
    public void writeEntry(LifeDecisionEventType event, UUID workItemId, WorkItem workItem) {
        // CREATED events are commitment-initiated — use writeEntry(event, record) instead
        if (event == LifeDecisionEventType.CREATED) return;

        findRecord(workItemId).ifPresent(record -> writeFromRecord(event, record, workItemId));
    }

    @Override
    public void writeEntry(LifeDecisionEventType event, LifeCommitmentRecord record) {
        writeFromRecord(event, record, record.workItemId);
    }

    private void writeFromRecord(LifeDecisionEventType event, LifeCommitmentRecord record, UUID workItemId) {
        // workItemId from record may be null for OVERSIGHT commitments before the gate is fulfilled —
        // this is expected; the CREATED entry records the gate opening, not task completion.
        FinancialDecisionLedgerEntry entry = new FinancialDecisionLedgerEntry();
        entry.subjectId        = record.id;
        entry.sequenceNumber   = DomainLedgerHandler.nextSequenceNumber(ledgerRepository, record.id);
        entry.entryType        = LedgerEntryType.EVENT;
        entry.actorId          = "life-system";
        entry.actorType        = ActorType.SYSTEM;
        entry.actorRole        = "FinancialDecisionAudit";
        entry.workItemId       = workItemId;
        entry.oversightRef     = record.id;
        entry.amountThreshold  = record.amountThreshold;
        entry.purchaseCategory = record.purchaseCategory;
        entry.approvedBy       = event == LifeDecisionEventType.COMPLETED ? record.approvedBy : null;
        entry.eventType        = event;
        ledgerRepository.save(entry, TenancyConstants.DEFAULT_TENANT_ID);
    }

    protected Optional<LifeCommitmentRecord> findRecord(UUID workItemId) {
        return LifeCommitmentRecord.findByWorkItemId(workItemId);
    }
}
