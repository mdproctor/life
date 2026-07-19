package io.casehub.life.app.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.life.api.HouseholdGroups;
import io.casehub.life.app.event.LifeEventBroadcaster;
import io.casehub.life.app.event.LifeEventType;
import io.casehub.life.app.event.LifeSseEvent;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Set;

@ApplicationScoped
@Path("/events")
public class LifeEventSseResource {

    private static final Logger LOG = Logger.getLogger(LifeEventSseResource.class);

    private static final Set<LifeEventType> INBOX_TYPES = Set.of(
            LifeEventType.WORK_ITEM_CREATED,
            LifeEventType.WORK_ITEM_UPDATED,
            LifeEventType.WORK_ITEM_COMPLETED,
            LifeEventType.SLA_BREACH
    );

    private static final Set<LifeEventType> CASE_TYPES = Set.of(
            LifeEventType.CASE_STARTED,
            LifeEventType.CASE_COMPLETED,
            LifeEventType.CASE_FAULTED
    );

    @Inject
    LifeEventBroadcaster broadcaster;

    @Inject
    ObjectMapper objectMapper;

    @GET
    @Path("/inbox")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RolesAllowed({HouseholdGroups.ADMIN, HouseholdGroups.MEMBER, HouseholdGroups.JUNIOR})
    public Multi<String> inbox() {
        return eventStream(INBOX_TYPES);
    }

    @GET
    @Path("/cases")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RolesAllowed({HouseholdGroups.ADMIN, HouseholdGroups.MEMBER})
    public Multi<String> cases() {
        return eventStream(CASE_TYPES);
    }

    private Multi<String> eventStream(Set<LifeEventType> filter) {
        Multi<String> events = Multi.createFrom().emitter(emitter -> {
            var sub = broadcaster.subscribe(event -> {
                if (filter.contains(event.type())) {
                    try {
                        emitter.emit(objectMapper.writeValueAsString(event));
                    } catch (Exception e) {
                        LOG.debugf(e, "Failed to serialize SSE event");
                    }
                }
            });
            emitter.onTermination(() -> sub.cancel());
        });

        Multi<String> heartbeat = Multi.createFrom()
                .ticks().every(Duration.ofSeconds(30))
                .map(tick -> ":keepalive\n");

        return Multi.createBy().merging().streams(events, heartbeat);
    }
}
