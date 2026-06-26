package io.casehub.life.app.infrastructure;

import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.watchdog.WatchdogConditionType;
import io.casehub.qhorus.runtime.channel.Channel;
import io.casehub.qhorus.runtime.channel.ChannelCreateRequest;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.gateway.ChannelGateway;
import io.casehub.qhorus.runtime.store.WatchdogStore;
import io.casehub.qhorus.runtime.watchdog.Watchdog;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Creates and registers life-domain Qhorus channels at startup.
 * One APPROVAL_PENDING Watchdog per channel monitors for expired Commitments.
 *
 * Channel topology:
 *   life/delegation  — shared, family task delegation (DELEGATION mode)
 *   life/oversight   — shared, major decision gates (OVERSIGHT mode), COMMAND+RESPONSE only
 *   life/actor/{id}  — per-ExternalActor, contractor commitments (CONTRACTOR mode)
 */
@ApplicationScoped
public class LifeChannelInitializer {

    public static final String DELEGATION_CHANNEL = "life/delegation";
    public static final String OVERSIGHT_CHANNEL  = "life/oversight";

    @Inject
    ChannelService channelService;

    @Inject
    ChannelGateway channelGateway;

    @Inject
    WatchdogStore watchdogStore;

    @Transactional
    void onStart(@Observes final StartupEvent ev) {
        ensureChannelWithWatchdog(DELEGATION_CHANNEL,
                List.of("household-admin", "household-member", "life-system"), null);
        ensureChannelWithWatchdog(OVERSIGHT_CHANNEL,
                List.of("household-admin", "life-system"),
                Set.of(MessageType.COMMAND, MessageType.RESPONSE));
    }

    /**
     * Creates and registers a per-actor contractor channel on demand.
     * Idempotent — safe to call multiple times for the same actor.
     * Returns the channel UUID for use in MessageDispatch.
     */
    @Transactional
    public UUID ensureActorChannel(final UUID externalActorId) {
        final String name = "life/actor/ext-" + externalActorId;
        return ensureChannelWithWatchdog(name,
                List.of("household-admin", "household-member", "life-system"), null);
    }

    /**
     * Returns the UUID for a named life channel (must already exist).
     */
    public UUID channelIdFor(final String channelName) {
        return channelService.findByName(channelName)
                .map(ch -> ch.id)
                .orElseThrow(() -> new IllegalStateException(
                        "Life channel not found: " + channelName + " — startup may not have run"));
    }

    private UUID ensureChannelWithWatchdog(
            final String name,
            final List<String> writers,
            final Set<MessageType> allowedTypes) {
        final String writersStr = String.join(",", writers);
        // ChannelService.create() does NOT register in ChannelGateway (GE-20260526-5247f2).
        // Always call initChannel() after create or find.
        return channelService.findByName(name)
                .map(ch -> {
                    channelGateway.initChannel(ch.id, new ChannelRef(ch.id, ch.name));
                    return ch.id;
                })
                .orElseGet(() -> {
                    final ChannelCreateRequest.Builder reqBuilder =
                            ChannelCreateRequest.builder(name)
                                    .description(name)
                                    .semantic(ChannelSemantic.APPEND)
                                    .allowedWriters(writersStr);
                    if (allowedTypes != null) {
                        reqBuilder.allowedTypes(allowedTypes);
                    }
                    final Channel ch = channelService.create(reqBuilder.build());
                    channelGateway.initChannel(ch.id, new ChannelRef(ch.id, ch.name));
                    // One APPROVAL_PENDING Watchdog per channel — thresholdSeconds=0 fires
                    // as soon as any Commitment.expiresAt passes.
                    final Watchdog w = new Watchdog();
                    w.id = UUID.randomUUID();
                    w.conditionType = WatchdogConditionType.APPROVAL_PENDING.name();
                    w.notificationChannel = name;
                    w.thresholdSeconds = 0;
                    w.createdBy = "life-system";
                    w.createdAt = Instant.now();
                    watchdogStore.put(w);
                    return ch.id;
                });
    }
}
