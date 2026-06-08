package io.casehub.life.app.service.ledger;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.life.api.LifeActorIds;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.app.LifeDecisionEventType;
import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.life.app.ledger.LegalActionLedgerEntry;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.work.runtime.model.WorkItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class LegalDomainLedgerHandler implements DomainLedgerHandler {

    private static final Logger LOG = Logger.getLogger(LegalDomainLedgerHandler.class);

    @Inject LedgerEntryRepository ledgerRepository;
    @Inject LifeOutcomeAttestationWriter attestationWriter;

    LegalDomainLedgerHandler(LedgerEntryRepository ledgerRepository,
                              LifeOutcomeAttestationWriter attestationWriter) {
        this.ledgerRepository = ledgerRepository;
        this.attestationWriter = attestationWriter;
    }

    LegalDomainLedgerHandler() {}

    @Override public LifeDomain domain() { return LifeDomain.LEGAL; }

    @Override
    public void writeEntry(LifeDecisionEventType event, UUID workItemId, WorkItem workItem) {
        Optional<LifeTaskContext> ctxOpt = findContext(workItemId);
        if (ctxOpt.isEmpty()) {
            LOG.warnf("LegalDomainLedgerHandler: no LifeTaskContext for workItemId=%s — skipping ledger write", workItemId);
            return;
        }
        LifeTaskContext ctx = ctxOpt.get();

        String actorId = ctx.externalActorId != null
                ? LifeActorIds.of(ctx.externalActorId) : "life-system";
        ActorType actorType = ctx.externalActorId != null ? ActorType.HUMAN : ActorType.SYSTEM;

        LegalActionLedgerEntry entry = new LegalActionLedgerEntry();
        entry.subjectId      = ctx.workItemId;
        entry.sequenceNumber = nextSequenceNumber(ctx.workItemId);
        entry.entryType      = LedgerEntryType.EVENT;
        entry.actorId        = actorId;
        entry.actorType      = actorType;
        entry.actorRole      = "LegalActionAudit";
        entry.workItemId     = ctx.workItemId;
        entry.legalObligation = workItem.title;
        entry.filingDeadline  = workItem.expiresAt;
        entry.eventType       = event;
        entry.actionTaken     = event == LifeDecisionEventType.COMPLETED ? workItem.outcome : null;

        ledgerRepository.save(entry);
        attestationWriter.attestOutcome(entry, event, ctx, workItem);
    }

    protected Optional<LifeTaskContext> findContext(UUID workItemId) {
        return LifeTaskContext.findByIdOptional(workItemId);
    }

    private int nextSequenceNumber(UUID subjectId) {
        return ledgerRepository.findLatestBySubjectId(subjectId)
                .map(e -> e.sequenceNumber + 1)
                .orElse(1);
    }
}
