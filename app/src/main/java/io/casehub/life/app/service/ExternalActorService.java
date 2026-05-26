package io.casehub.life.app.service;

import io.casehub.life.api.LifeActorType;
import io.casehub.life.api.model.HouseholdTaskStatus;
import io.casehub.life.app.entity.ExternalActor;
import io.casehub.life.app.entity.HouseholdTask;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ExternalActorService {

    @Transactional
    public ExternalActor create(final ExternalActor actor) {
        actor.persist();
        return actor;
    }

    public Optional<ExternalActor> findById(final UUID id) {
        return ExternalActor.findByIdOptional(id);
    }

    public List<ExternalActor> list(final LifeActorType actorType) {
        if (actorType != null) {
            return ExternalActor.list("actorType", actorType);
        }
        return ExternalActor.listAll();
    }

    @Transactional
    public Optional<ExternalActor> update(final UUID id, final ExternalActor update) {
        return ExternalActor.<ExternalActor>findByIdOptional(id).map(existing -> {
            existing.name = update.name;
            existing.actorType = update.actorType;
            existing.contactMethod = update.contactMethod;
            existing.contactValue = update.contactValue;
            return existing;
        });
    }

    /** Deletes the actor. Throws NotFoundException (404) if absent, ClientErrorException (409)
     *  if tasks reference it. Check and delete in one transaction to avoid TOCTOU. */
    @Transactional
    public void delete(final UUID id) {
        final ExternalActor actor = ExternalActor.<ExternalActor>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        final long referencingTasks = HouseholdTask.count("externalActorId", id);
        if (referencingTasks > 0) {
            throw new ClientErrorException(
                    "ExternalActor is referenced by " + referencingTasks + " task(s)",
                    Response.Status.CONFLICT);
        }
        actor.delete();
    }

    public List<HouseholdTask> listTasks(final UUID actorId, final HouseholdTaskStatus status) {
        if (status != null) {
            return HouseholdTask.list("externalActorId = ?1 and status = ?2", actorId, status);
        }
        return HouseholdTask.list("externalActorId", actorId);
    }
}
