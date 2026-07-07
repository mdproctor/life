package io.casehub.life.app.engine;

import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.life.app.infrastructure.LifeChannelInitializer;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.query.MessageQuery;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.work.runtime.model.WorkItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class LifeChannelContextProvider {

    private static final Logger LOG = Logger.getLogger(LifeChannelContextProvider.class);

    private final ChannelService channelService;
    private final MessageStore messageStore;
    private final int messageLimit;

    @Inject
    public LifeChannelContextProvider(
            ChannelService channelService,
            MessageStore messageStore,
            @ConfigProperty(name = "casehub.life.channel-context.message-limit", defaultValue = "10")
            int messageLimit) {
        this.channelService = channelService;
        this.messageStore = messageStore;
        this.messageLimit = messageLimit;
    }

    @Transactional
    public Map<String, Object> gatherContext(UUID caseId) {
        Map<String, Object> channelContext = new LinkedHashMap<>();

        queryChannel(LifeChannelInitializer.DELEGATION_CHANNEL, "delegation")
                .ifPresent(msgs -> channelContext.put("delegation", msgs));
        queryChannel(LifeChannelInitializer.OVERSIGHT_CHANNEL, "oversight")
                .ifPresent(msgs -> channelContext.put("oversight", msgs));

        resolveActorChannels(caseId).forEach((channelName, label) ->
                queryChannel(channelName, label)
                        .ifPresent(msgs -> channelContext.put(label, msgs)));

        return Map.of("channelContext", channelContext);
    }

    private Optional<List<Map<String, Object>>> queryChannel(String channelName, String label) {
        return channelService.findByName(channelName)
                .map(channel -> {
                    List<Message> messages = messageStore.scan(
                            MessageQuery.builder()
                                    .channelId(channel.id())
                                    .limit(messageLimit)
                                    .descending(true)
                                    .build());
                    List<Message> chronological = new ArrayList<>(messages);
                    Collections.reverse(chronological);
                    return chronological.stream()
                            .map(this::serializeMessage)
                            .toList();
                });
    }

    private Map<String, Object> serializeMessage(Message msg) {
        return Map.of(
                "sender", msg.sender() != null ? msg.sender() : "",
                "type", msg.messageType() != null ? msg.messageType().name() : "",
                "content", msg.content() != null ? msg.content() : "",
                "createdAt", msg.createdAt() != null ? msg.createdAt().toString() : "");
    }

    protected Map<String, String> resolveActorChannels(UUID caseId) {
        String callerRefPrefix = "case:" + caseId + "/";
        List<WorkItem> workItems = WorkItem.list("callerRef LIKE ?1", callerRefPrefix + "%");

        Map<String, String> actorChannels = new LinkedHashMap<>();
        for (WorkItem wi : workItems) {
            LifeTaskContext.findByIdOptional(wi.id)
                    .map(obj -> (LifeTaskContext) obj)
                    .filter(ctx -> ctx.externalActorId != null)
                    .ifPresent(ctx -> {
                        String channelName = "life/actor/ext-" + ctx.externalActorId;
                        actorChannels.put(channelName, "actor/ext-" + ctx.externalActorId);
                    });
        }
        return actorChannels;
    }
}
