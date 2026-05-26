package io.casehub.life.app.service;

import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.model.HouseholdTaskStatus;
import io.casehub.life.app.entity.HouseholdTask;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class HouseholdTaskService {

    @Transactional
    public HouseholdTask create(final HouseholdTask task) {
        task.persist();
        return task;
    }

    public Optional<HouseholdTask> findById(final UUID id) {
        return HouseholdTask.findByIdOptional(id);
    }

    public List<HouseholdTask> list(final LifeDomain domain,
                                    final HouseholdTaskStatus status,
                                    final String assignedTo,
                                    final UUID externalActorId) {
        final StringBuilder query = new StringBuilder();
        final Map<String, Object> params = new HashMap<>();

        if (domain != null) {
            query.append("domain = :domain");
            params.put("domain", domain);
        }
        if (status != null) {
            if (!query.isEmpty()) query.append(" and ");
            query.append("status = :status");
            params.put("status", status);
        }
        if (assignedTo != null) {
            if (!query.isEmpty()) query.append(" and ");
            query.append("assignedTo = :assignedTo");
            params.put("assignedTo", assignedTo);
        }
        if (externalActorId != null) {
            if (!query.isEmpty()) query.append(" and ");
            query.append("externalActorId = :externalActorId");
            params.put("externalActorId", externalActorId);
        }

        if (query.isEmpty()) {
            return HouseholdTask.listAll();
        }
        return HouseholdTask.list(query.toString(), params);
    }

    @Transactional
    public Optional<HouseholdTask> update(final UUID id, final HouseholdTask update) {
        return HouseholdTask.<HouseholdTask>findByIdOptional(id).map(existing -> {
            existing.domain = update.domain;
            existing.title = update.title;
            existing.description = update.description;
            existing.deadline = update.deadline;
            existing.slaHours = update.slaHours;
            existing.status = update.status;
            existing.assignedTo = update.assignedTo;
            existing.externalActorId = update.externalActorId;
            existing.recurrence = update.recurrence;
            return existing;
        });
    }

    /** Deletes the task. Throws NotFoundException (404) if absent. */
    @Transactional
    public void delete(final UUID id) {
        final HouseholdTask task = HouseholdTask.<HouseholdTask>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        task.delete();
    }
}
