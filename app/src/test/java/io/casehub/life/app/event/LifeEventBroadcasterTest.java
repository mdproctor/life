package io.casehub.life.app.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LifeEventBroadcasterTest {

    @Test
    void subscriberReceivesPublishedEvent() {
        var broadcaster = new LifeEventBroadcaster();
        var received = new ArrayList<LifeSseEvent>();

        broadcaster.subscribe(received::add);
        broadcaster.publish(LifeSseEvent.of(LifeEventType.CASE_STARTED, Map.of("caseId", "123")));

        assertThat(received).hasSize(1);
        assertThat(received.getFirst().type()).isEqualTo(LifeEventType.CASE_STARTED);
        assertThat(received.getFirst().data()).containsEntry("caseId", "123");
    }

    @Test
    void multipleSubscribersReceiveEvent() {
        var broadcaster = new LifeEventBroadcaster();
        var received1 = new ArrayList<LifeSseEvent>();
        var received2 = new ArrayList<LifeSseEvent>();

        broadcaster.subscribe(received1::add);
        broadcaster.subscribe(received2::add);
        broadcaster.publish(LifeSseEvent.of(LifeEventType.SLA_BREACH, Map.of()));

        assertThat(received1).hasSize(1);
        assertThat(received2).hasSize(1);
    }

    @Test
    void cancelledSubscriptionDoesNotReceive() {
        var broadcaster = new LifeEventBroadcaster();
        var received = new ArrayList<LifeSseEvent>();

        var sub = broadcaster.subscribe(received::add);
        sub.cancel();
        broadcaster.publish(LifeSseEvent.of(LifeEventType.CASE_COMPLETED, Map.of()));

        assertThat(received).isEmpty();
    }

    @Test
    void failingSubscriberDoesNotAffectOthers() {
        var broadcaster = new LifeEventBroadcaster();
        var received = new ArrayList<LifeSseEvent>();

        broadcaster.subscribe(e -> { throw new RuntimeException("boom"); });
        broadcaster.subscribe(received::add);
        broadcaster.publish(LifeSseEvent.of(LifeEventType.WORK_ITEM_CREATED, Map.of()));

        assertThat(received).hasSize(1);
    }
}
