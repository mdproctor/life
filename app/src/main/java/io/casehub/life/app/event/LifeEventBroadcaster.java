package io.casehub.life.app.event;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@ApplicationScoped
public class LifeEventBroadcaster {

    private static final Logger LOG = Logger.getLogger(LifeEventBroadcaster.class);

    private final List<SubscriptionHandle> subscribers = new CopyOnWriteArrayList<>();

    public Subscription subscribe(Consumer<LifeSseEvent> listener) {
        var handle = new SubscriptionHandle(listener);
        subscribers.add(handle);
        return handle;
    }

    public void publish(LifeSseEvent event) {
        for (var sub : subscribers) {
            if (sub.active) {
                try {
                    sub.listener.accept(event);
                } catch (Exception e) {
                    LOG.debugf(e, "SSE subscriber threw — best-effort delivery");
                }
            }
        }
    }

    public interface Subscription {
        void cancel();
    }

    private class SubscriptionHandle implements Subscription {
        final Consumer<LifeSseEvent> listener;
        volatile boolean active = true;

        SubscriptionHandle(Consumer<LifeSseEvent> listener) {
            this.listener = listener;
        }

        @Override
        public void cancel() {
            active = false;
            subscribers.remove(this);
        }
    }
}
