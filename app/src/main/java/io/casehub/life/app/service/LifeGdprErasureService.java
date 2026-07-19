package io.casehub.life.app.service;

import io.casehub.ledger.api.model.ErasureReason;
import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.privacy.LedgerErasureService;
import io.casehub.life.api.LifeActorIds;
import io.casehub.life.api.response.ErasureResponse;
import io.casehub.life.app.entity.ExternalActor;
import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.life.app.service.ledger.LifeLedgerWriter;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryCapabilityException;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.work.runtime.model.WorkItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class LifeGdprErasureService {

    private static final Logger LOG = Logger.getLogger(LifeGdprErasureService.class);

    @Inject LedgerErasureService ledgerErasureService;
    @Inject CaseMemoryStore memoryStore;
    @Inject LifeLedgerWriter lifeLedgerWriter;
    @Inject LedgerConfig ledgerConfig;

    LifeGdprErasureService() {}

    LifeGdprErasureService(LedgerErasureService ledgerErasureService,
                            CaseMemoryStore memoryStore,
                            LifeLedgerWriter lifeLedgerWriter,
                            LedgerConfig ledgerConfig) {
        this.ledgerErasureService = ledgerErasureService;
        this.memoryStore = memoryStore;
        this.lifeLedgerWriter = lifeLedgerWriter;
        this.ledgerConfig = ledgerConfig;
    }

    @Transactional
    public ErasureResponse erase(final UUID id, final String erasedBy) {
        ExternalActor actor = findActor(id);

        if (actor.gdprErasedAt != null) {
            throw new WebApplicationException(
                    "ExternalActor already erased at " + actor.gdprErasedAt, 409);
        }

        long activeTasks = findActiveTaskCount(id);
        if (activeTasks > 0) {
            throw new WebApplicationException(
                    "ExternalActor has " + activeTasks + " active task(s) — close before erasure", 409);
        }

        actor.name = "[ERASED]";
        actor.contactValue = "[ERASED]";
        actor.gdprErasedAt = Instant.now();

        int memoryRecordsErased;
        try {
            memoryRecordsErased = memoryStore.eraseEntity(
                    LifeActorIds.of(id), TenancyConstants.DEFAULT_TENANT_ID);
        } catch (MemoryCapabilityException e) {
            LOG.debugf("Memory store does not support eraseEntity: %s", e.getMessage());
            memoryRecordsErased = 0;
        }

        var erasureResult = ledgerErasureService.erase(
                LifeActorIds.of(id), ErasureReason.GDPR_ART_17_REQUEST);

        lifeLedgerWriter.writeErasureEntry(actor, erasedBy,
                memoryRecordsErased, erasureResult.affectedEntryCount());

        return new ErasureResponse(
                id,
                actor.gdprErasedAt,
                memoryRecordsErased,
                erasureResult.affectedEntryCount(),
                ledgerConfig.identity().tokenisation().enabled());
    }

    protected ExternalActor findActor(UUID id) {
        return ExternalActor.<ExternalActor>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
    }

    protected long findActiveTaskCount(UUID externalActorId) {
        return LifeTaskContext.<LifeTaskContext>list("externalActorId", externalActorId)
                .stream()
                .filter(ctx -> {
                    var wi = WorkItem.<WorkItem>findByIdOptional(ctx.workItemId).orElse(null);
                    return wi != null && wi.status.isActive();
                })
                .count();
    }
}
