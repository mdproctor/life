package io.casehub.life.app.service;

import io.casehub.ledger.runtime.service.TrustGateService;
import io.casehub.life.api.LifeActorIds;
import io.casehub.life.api.LifeActorType;
import io.casehub.life.api.request.CreateExternalActorRequest;
import io.casehub.life.api.request.UpdateExternalActorRequest;
import io.casehub.life.api.response.ExternalActorResponse;
import io.casehub.life.api.response.LifeTaskContextResponse;
import io.casehub.life.app.entity.ExternalActor;
import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.life.app.service.ledger.LifeLedgerWriter;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.memory.CaseMemoryStore;
import io.casehub.memory.MemoryCapabilityException;
import io.casehub.work.runtime.model.WorkItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ExternalActorService {

    private static final Logger LOG = Logger.getLogger(ExternalActorService.class);

    @Inject
    LifeLedgerWriter lifeLedgerWriter;

    @Inject
    TrustGateService trustGateService;

    @Inject
    CaseMemoryStore memoryStore;

    @Transactional
    public ExternalActorResponse create(final CreateExternalActorRequest req) {
        final ExternalActor actor = new ExternalActor();
        actor.name = req.name();
        actor.actorType = req.actorType();
        actor.contactMethod = req.contactMethod();
        actor.contactValue = req.contactValue();
        actor.persist();
        return toResponse(actor);
    }

    public Optional<ExternalActorResponse> findById(final UUID id) {
        return ExternalActor.<ExternalActor>findByIdOptional(id).map(this::toResponse);
    }

    public List<ExternalActorResponse> list(final LifeActorType actorType) {
        List<ExternalActor> actors = actorType != null
                ? ExternalActor.list("actorType", actorType)
                : ExternalActor.listAll();
        return actors.stream().map(this::toResponse).toList();
    }

    @Transactional
    public Optional<ExternalActorResponse> update(final UUID id, final UpdateExternalActorRequest req) {
        return ExternalActor.<ExternalActor>findByIdOptional(id).map(existing -> {
            existing.name = req.name();
            existing.actorType = req.actorType();
            existing.contactMethod = req.contactMethod();
            existing.contactValue = req.contactValue();
            return toResponse(existing);
        });
    }

    @Transactional
    public void delete(final UUID id) {
        final ExternalActor actor = ExternalActor.<ExternalActor>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        final long referencingTasks = LifeTaskContext.count("externalActorId", id);
        if (referencingTasks > 0) {
            throw new ClientErrorException(
                    "ExternalActor is referenced by " + referencingTasks + " task(s)",
                    Response.Status.CONFLICT);
        }
        actor.delete();
    }

    @Transactional
    public void erase(final UUID id, final String erasedBy) {
        final ExternalActor actor = ExternalActor.<ExternalActor>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);

        if (actor.gdprErasedAt != null) {
            throw new WebApplicationException(
                    "ExternalActor already erased at " + actor.gdprErasedAt, 409);
        }

        final long activeTasks = LifeTaskContext.<LifeTaskContext>list("externalActorId", id)
                .stream()
                .filter(ctx -> {
                    final var wi = WorkItem.<WorkItem>findByIdOptional(ctx.workItemId).orElse(null);
                    return wi != null && wi.status.isActive();
                })
                .count();
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

        lifeLedgerWriter.writeErasureEntry(actor, erasedBy, memoryRecordsErased);
    }

    public List<LifeTaskContextResponse> listTasks(final UUID actorId) {
        return LifeTaskContext.<LifeTaskContext>list("externalActorId", actorId)
                .stream()
                .map(c -> new LifeTaskContextResponse(c.workItemId, c.domain, c.externalActorId, c.recurrence, c.jurisdiction))
                .toList();
    }

    private ExternalActorResponse toResponse(final ExternalActor actor) {
        ExternalActorResponse.TrustProfile profile;
        if (actor.gdprErasedAt != null) {
            profile = ExternalActorResponse.TrustProfile.EMPTY;
        } else {
            String actorId = LifeActorIds.of(actor.id);
            java.util.OptionalDouble globalOpt = trustGateService.currentScore(actorId);
            Double global = globalOpt.isPresent() ? globalOpt.getAsDouble() : null;
            Map<String, Double> dimensions = trustGateService.allDimensionScores(actorId);
            Map<String, Double> capabilities = trustGateService.allCapabilityScores(actorId);
            profile = new ExternalActorResponse.TrustProfile(global, dimensions, capabilities);
        }
        return new ExternalActorResponse(
                actor.id,
                actor.name,
                actor.actorType,
                actor.contactMethod,
                actor.contactValue,
                actor.createdAt,
                actor.gdprErasedAt,
                profile
        );
    }
}
