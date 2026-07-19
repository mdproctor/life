package io.casehub.life.app.event;

import java.time.Instant;
import java.util.Map;

public record LifeSseEvent(
        LifeEventType type,
        Map<String, Object> data,
        Instant timestamp
) {
    public static LifeSseEvent of(LifeEventType type, Map<String, Object> data) {
        return new LifeSseEvent(type, data, Instant.now());
    }
}
