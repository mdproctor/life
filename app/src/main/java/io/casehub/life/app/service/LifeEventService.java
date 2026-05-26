package io.casehub.life.app.service;

import io.casehub.life.api.LifeDomain;
import io.casehub.life.app.entity.LifeEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class LifeEventService {

    @Transactional
    public LifeEvent create(final LifeEvent event) {
        event.persist();
        return event;
    }

    public Optional<LifeEvent> findById(final UUID id) {
        return LifeEvent.findByIdOptional(id);
    }

    public List<LifeEvent> list(final LifeDomain domain) {
        if (domain != null) {
            return LifeEvent.list("domain", domain);
        }
        return LifeEvent.listAll();
    }

    /** Deletes the event. Throws NotFoundException (404) if absent. */
    @Transactional
    public void delete(final UUID id) {
        final LifeEvent event = LifeEvent.<LifeEvent>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        event.delete();
    }
}
