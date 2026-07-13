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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

import io.casehub.life.api.response.PagedResponse;
import io.quarkus.panache.common.Page;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ExternalActorService {

    @Inject
    TrustGateService trustGateService;

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

    @Transactional
    public PagedResponse<ExternalActorResponse> search(
            final String name, final LifeActorType actorType, final String contactMethod,
            final boolean erasedOnly, int page, int size) {
        page = Math.max(page, 0);
        size = Math.max(Math.min(size, 100), 1);
        var params     = new HashMap<String, Object>();
        var conditions = new ArrayList<String>();

        if (name != null && !name.isBlank()) {
            conditions.add("LOWER(name) LIKE LOWER(:name)");
            params.put("name", "%" + name + "%");
        }
        if (actorType != null) {
            conditions.add("actorType = :actorType");
            params.put("actorType", actorType);
        }
        if (contactMethod != null && !contactMethod.isBlank()) {
            conditions.add("contactMethod = :contactMethod");
            params.put("contactMethod", contactMethod);
        }
        if (erasedOnly) {
            conditions.add("gdprErasedAt IS NOT NULL");
        }

        String query = conditions.isEmpty() ? "" : String.join(" AND ", conditions);
        var panacheQuery = query.isEmpty()
                           ? ExternalActor.<ExternalActor>findAll()
                           : ExternalActor.<ExternalActor>find(query, params);

        long                        total  = panacheQuery.count();
        List<ExternalActor>         actors = panacheQuery.page(Page.of(page, size)).list();
        List<ExternalActorResponse> items  = actors.stream().map(this::toResponse).toList();
        return new PagedResponse<>(items, page, size, total);
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
