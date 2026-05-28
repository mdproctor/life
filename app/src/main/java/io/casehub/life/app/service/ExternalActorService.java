package io.casehub.life.app.service;

import io.casehub.life.api.LifeActorType;
import io.casehub.life.api.request.CreateExternalActorRequest;
import io.casehub.life.api.request.UpdateExternalActorRequest;
import io.casehub.life.api.response.ExternalActorResponse;
import io.casehub.life.app.entity.ExternalActor;
import io.casehub.life.app.entity.LifeTaskContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ExternalActorService {

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
        // LifeTaskContext referential integrity check enabled in Task 5 once entity is mapped.
        actor.delete();
    }

    public List<LifeTaskContext> listTasks(final UUID actorId) {
        // LifeTaskContext query enabled in Task 5 once entity is mapped.
        return List.of();
    }

    private ExternalActorResponse toResponse(final ExternalActor actor) {
        return new ExternalActorResponse(
                actor.id,
                actor.name,
                actor.actorType,
                actor.contactMethod,
                actor.contactValue,
                actor.createdAt
        );
    }
}
