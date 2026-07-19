package io.casehub.life.app.event;

import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import io.casehub.work.runtime.event.SlaBreachEvent;
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
public class LifeEventBridge {

    @Inject
    LifeEventBroadcaster broadcaster;

    public void onWorkItemEvent(@ObservesAsync WorkItemLifecycleEvent event) {
        String typeSuffix = event.type().substring(event.type().lastIndexOf('.') + 1);
        LifeEventType type = switch (typeSuffix) {
            case "created" -> LifeEventType.WORK_ITEM_CREATED;
            case "completed" -> LifeEventType.WORK_ITEM_COMPLETED;
            default -> LifeEventType.WORK_ITEM_UPDATED;
        };
        broadcaster.publish(LifeSseEvent.of(type, Map.of(
                "workItemId", event.workItemId().toString(),
                "eventType", typeSuffix
        )));
    }

    public void onCaseEvent(@ObservesAsync CaseLifecycleEvent event) {
        LifeEventType type = switch (event.eventType()) {
            case "CaseStarted" -> LifeEventType.CASE_STARTED;
            case "CaseCompleted" -> LifeEventType.CASE_COMPLETED;
            case "CaseFaulted" -> LifeEventType.CASE_FAULTED;
            default -> null;
        };
        if (type == null) return;
        broadcaster.publish(LifeSseEvent.of(type, Map.of(
                "caseId", event.caseId().toString(),
                "eventType", event.eventType()
        )));
    }

    public void onSlaBreachEvent(@ObservesAsync SlaBreachEvent event) {
        broadcaster.publish(LifeSseEvent.of(LifeEventType.SLA_BREACH, Map.of(
                "workItemId", event.context().task().taskId().toString()
        )));
    }
}
