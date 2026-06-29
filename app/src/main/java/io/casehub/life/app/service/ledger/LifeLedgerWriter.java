package io.casehub.life.app.service.ledger;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.life.app.entity.ExternalActor;
import io.casehub.life.app.ledger.ExternalActorErasureLedgerEntry;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.TenancyConstants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@ApplicationScoped
public class LifeLedgerWriter {

    @Inject
    LedgerEntryRepository ledgerRepository;

    public void writeErasureEntry(final ExternalActor actor, final String erasedBy,
                                   final int memoryRecordsErased) {
        ExternalActorErasureLedgerEntry entry = new ExternalActorErasureLedgerEntry();
        populateBase(entry, actor.id, erasedBy, ActorType.HUMAN, "GdprDataController");
        entry.erasedActorId = actor.id;
        entry.contactMethod = actor.contactMethod;
        entry.erasedBy = erasedBy;
        entry.memoryRecordsErased = memoryRecordsErased;
        ledgerRepository.save(entry, TenancyConstants.DEFAULT_TENANT_ID);
    }

    private void populateBase(LedgerEntry entry, UUID subjectId,
                              String actorId, ActorType actorType, String actorRole) {
        entry.subjectId      = subjectId;
        entry.sequenceNumber = DomainLedgerHandler.nextSequenceNumber(ledgerRepository, subjectId);
        entry.entryType      = LedgerEntryType.EVENT;
        entry.actorId        = actorId;
        entry.actorType      = actorType;
        entry.actorRole      = actorRole;
    }
}
