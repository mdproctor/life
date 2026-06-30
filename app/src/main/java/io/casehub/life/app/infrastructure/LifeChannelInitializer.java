package io.casehub.life.app.infrastructure;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.WatchdogStore;
import io.casehub.qhorus.api.watchdog.Watchdog;
import io.casehub.qhorus.api.watchdog.WatchdogConditionType;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.gateway.ChannelGateway;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

    @Transactional
    public UUID ensureActorChannel(final UUID externalActorId) {
        final String name = "life/actor/ext-" + externalActorId;
        return ensureChannelWithWatchdog(name,
                List.of("household-admin", "household-member", "life-system"), null);
    }

    public UUID channelIdFor(final String channelName) {
        return channelService.findByName(channelName)
                .map(Channel::id)
                .orElseThrow(() -> new IllegalStateException(
                        "Life channel not found: " + channelName + " — startup may not have run"));
    }

    private UUID ensureChannelWithWatchdog(
            final String name,
            final List<String> writers,
            final Set<MessageType> allowedTypes) {
        return channelService.findByName(name)
                .map(ch -> {
                    channelGateway.initChannel(ch.id(), new ChannelRef(ch.id(), ch.name()));
                    return ch.id();
                })
                .orElseGet(() -> {
                    final ChannelCreateRequest.Builder reqBuilder =
                            ChannelCreateRequest.builder(name)
                                    .description(name)
                                    .semantic(ChannelSemantic.APPEND)
                                    .allowedWriters(writers);
                    if (allowedTypes != null) {
                        reqBuilder.allowedTypes(allowedTypes);
                    }
                    final Channel ch = channelService.create(reqBuilder.build());
                    channelGateway.initChannel(ch.id(), new ChannelRef(ch.id(), ch.name()));
                    final Watchdog w = Watchdog.builder(
                                    WatchdogConditionType.APPROVAL_PENDING.name(), name)
                            .id(UUID.randomUUID())
                            .thresholdSeconds(0)
                            .notificationChannel(name)
                            .createdBy("life-system")
                            .createdAt(Instant.now())
                            .build();
                    watchdogStore.put(w);
                    return ch.id();
                });
    }
}
