package io.casehub.life.app.api;

import io.casehub.life.api.HouseholdGroups;
import io.casehub.life.app.event.LifeEventBroadcaster;
import io.casehub.life.app.event.LifeEventType;
import io.casehub.life.app.event.LifeSseEvent;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformStream;
import io.casehub.platform.api.mcp.RestPath;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;

@McpDomain(value = "life/events", app = "life", basePath = "/events", summary = "Watch inbox events (work items, SLA breaches); Watch case lifecycle events; Watch all life events")
@ApplicationScoped
public class LifeEventStreamApi {

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

    @Inject LifeEventBroadcaster broadcaster;

    @PlatformStream("Watch inbox events (work items, SLA breaches)")
    @RestPath("/inbox")
    @RolesAllowed({HouseholdGroups.ADMIN, HouseholdGroups.MEMBER, HouseholdGroups.JUNIOR})
    public Multi<LifeSseEvent> inbox() {
        return filteredStream(INBOX_TYPES);
    }

    @PlatformStream("Watch case lifecycle events")
    @RestPath("/cases")
    @RolesAllowed({HouseholdGroups.ADMIN, HouseholdGroups.MEMBER})
    public Multi<LifeSseEvent> cases() {
        return filteredStream(CASE_TYPES);
    }

    @PlatformStream("Watch all life events")
    @RestPath("/stream")
    @RolesAllowed({HouseholdGroups.ADMIN, HouseholdGroups.MEMBER, HouseholdGroups.JUNIOR})
    public Multi<LifeSseEvent> stream() {
        return Multi.createFrom().<LifeSseEvent>emitter(emitter -> {
            var sub = broadcaster.subscribe(emitter::emit);
            emitter.onTermination(sub::cancel);
        });
    }

    private Multi<LifeSseEvent> filteredStream(Set<LifeEventType> filter) {
        return Multi.createFrom().<LifeSseEvent>emitter(emitter -> {
            var sub = broadcaster.subscribe(event -> {
                if (filter.contains(event.type())) {
                    emitter.emit(event);
                }
            });
            emitter.onTermination(sub::cancel);
        });
    }
}
