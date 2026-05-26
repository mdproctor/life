package io.casehub.life.app.service;

import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.model.LifeGoalStatus;
import io.casehub.life.app.entity.LifeGoal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class LifeGoalService {

    @Transactional
    public LifeGoal create(final LifeGoal goal) {
        goal.persist();
        return goal;
    }

    public Optional<LifeGoal> findById(final UUID id) {
        return LifeGoal.findByIdOptional(id);
    }

    public List<LifeGoal> list(final LifeDomain domain, final LifeGoalStatus status) {
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

        if (query.isEmpty()) {
            return LifeGoal.listAll();
        }
        return LifeGoal.list(query.toString(), params);
    }

    @Transactional
    public Optional<LifeGoal> update(final UUID id, final LifeGoal update) {
        return LifeGoal.<LifeGoal>findByIdOptional(id).map(existing -> {
            existing.domain = update.domain;
            existing.title = update.title;
            existing.description = update.description;
            existing.targetDate = update.targetDate;
            existing.status = update.status;
            return existing;
        });
    }

    /** Deletes the goal. Throws NotFoundException (404) if absent. */
    @Transactional
    public void delete(final UUID id) {
        final LifeGoal goal = LifeGoal.<LifeGoal>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        goal.delete();
    }
}
