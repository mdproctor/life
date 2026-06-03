package io.casehub.life.app.service.ledger;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.life.api.LifeActorIds;
import io.casehub.life.app.LifeDecisionEventType;
import io.casehub.life.app.entity.ExternalActor;
import io.casehub.life.app.entity.LifeCommitmentRecord;
import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.life.app.ledger.ExternalActorErasureLedgerEntry;
import io.casehub.life.app.ledger.FinancialDecisionLedgerEntry;
import io.casehub.life.app.ledger.HealthDecisionLedgerEntry;
import io.casehub.life.app.ledger.LegalActionLedgerEntry;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.work.runtime.model.WorkItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@ApplicationScoped
public class LifeLedgerWriter {

    @Inject
    LedgerEntryRepository ledgerRepository;

    @Inject
    LifeOutcomeAttestationWriter attestationWriter;

    public void writeHealthEntry(final LifeDecisionEventType eventType,
                                  final LifeTaskContext ctx,
                                  final WorkItem workItem) {
        final String actorId = ctx.externalActorId != null
                ? LifeActorIds.of(ctx.externalActorId)
                : "life-system";
        final ActorType actorType = ctx.externalActorId != null
                ? ActorType.HUMAN
                : ActorType.SYSTEM;

        final var entry = new HealthDecisionLedgerEntry();
        populateBase(entry, ctx.workItemId, actorId, actorType, "HealthDecisionAudit");
        entry.workItemId = ctx.workItemId;
        entry.providerId = ctx.externalActorId;
        entry.taskCategory = workItem.category;
        entry.slaDeadline = workItem.expiresAt;
        entry.eventType = eventType;
        entry.outcome = eventType == LifeDecisionEventType.COMPLETED ? workItem.outcome : null;
        ledgerRepository.save(entry);
        attestationWriter.attestOutcome(entry, eventType, ctx, workItem);
    }

    public void writeFinancialEntry(final LifeDecisionEventType eventType,
                                     final LifeCommitmentRecord record,
                                     final UUID workItemId) {
        final var entry = new FinancialDecisionLedgerEntry();
        populateBase(entry, record.id, "life-system", ActorType.SYSTEM, "FinancialDecisionAudit");
        entry.workItemId = workItemId;
        entry.oversightRef = record.id;
        entry.amountThreshold = record.amountThreshold;
        entry.purchaseCategory = record.purchaseCategory;
        entry.approvedBy = eventType == LifeDecisionEventType.COMPLETED ? record.approvedBy : null;
        entry.eventType = eventType;
        ledgerRepository.save(entry);
    }

    public void writeLegalEntry(final LifeDecisionEventType eventType,
                                 final LifeTaskContext ctx,
                                 final WorkItem workItem) {
        final String actorId = ctx.externalActorId != null
                ? LifeActorIds.of(ctx.externalActorId)
                : "life-system";
        final ActorType actorType = ctx.externalActorId != null
                ? ActorType.HUMAN
                : ActorType.SYSTEM;

        final var entry = new LegalActionLedgerEntry();
        populateBase(entry, ctx.workItemId, actorId, actorType, "LegalActionAudit");
        entry.workItemId = ctx.workItemId;
        entry.legalObligation = workItem.title;
        entry.filingDeadline = workItem.expiresAt;
        entry.eventType = eventType;
        entry.actionTaken = eventType == LifeDecisionEventType.COMPLETED ? workItem.outcome : null;
        ledgerRepository.save(entry);
        attestationWriter.attestOutcome(entry, eventType, ctx, workItem);
    }

    public void writeErasureEntry(final ExternalActor actor, final String erasedBy) {
        final var entry = new ExternalActorErasureLedgerEntry();
        populateBase(entry, actor.id, erasedBy, ActorType.HUMAN, "GdprDataController");
        entry.erasedActorId = actor.id;
        entry.contactMethod = actor.contactMethod;
        entry.erasedBy = erasedBy;
        ledgerRepository.save(entry);
    }

    private void populateBase(final LedgerEntry entry, final UUID subjectId,
                               final String actorId, final ActorType actorType,
                               final String actorRole) {
        entry.subjectId = subjectId;
        entry.sequenceNumber = nextSequenceNumber(subjectId);
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = actorId;
        entry.actorType = actorType;
        entry.actorRole = actorRole;
    }

    private int nextSequenceNumber(final UUID subjectId) {
        return ledgerRepository.findLatestBySubjectId(subjectId)
                .map(e -> e.sequenceNumber + 1)
                .orElse(1);
    }
}
