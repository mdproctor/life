package io.casehub.life.app.engine;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.query.MessageQuery;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class LifeChannelContextProviderTest {

    private ChannelService channelService;
    private MessageStore messageStore;
    private LifeChannelContextProvider provider;

    private final UUID delegationChannelId = UUID.randomUUID();
    private final UUID oversightChannelId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        channelService = mock(ChannelService.class);
        messageStore = mock(MessageStore.class);

        when(channelService.findByName("life/delegation"))
                .thenReturn(Optional.of(channelWithId(delegationChannelId)));
        when(channelService.findByName("life/oversight"))
                .thenReturn(Optional.of(channelWithId(oversightChannelId)));

        when(messageStore.scan(any(MessageQuery.class))).thenReturn(List.of());

        provider = new LifeChannelContextProvider(channelService, messageStore, 10) {
            @Override protected Map<String, String> resolveActorChannels(UUID caseId) {
                return Map.of();
            }
        };
    }

    @Test
    void gatherContext_alwaysQueriesDelegationAndOversight() {
        UUID caseId = UUID.randomUUID();

        Map<String, Object> result = provider.gatherContext(caseId);

        @SuppressWarnings("unchecked")
        var ctx = (Map<String, Object>) result.get("channelContext");
        assertThat(ctx).containsKeys("delegation", "oversight");
    }

    @Test
    void gatherContext_serializesMessagesInChronologicalOrder() {
        UUID caseId = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-07-07T10:00:00Z");
        Instant t2 = Instant.parse("2026-07-07T11:00:00Z");

        Message older = message("finance-agent", MessageType.STATUS, "Budget OK", t1);
        Message newer = message("home-agent", MessageType.STATUS, "Task done", t2);

        when(messageStore.scan(argThat(q -> delegationChannelId.equals(q.channelId()))))
                .thenReturn(List.of(newer, older));

        Map<String, Object> result = provider.gatherContext(caseId);

        @SuppressWarnings("unchecked")
        var ctx = (Map<String, Object>) result.get("channelContext");
        @SuppressWarnings("unchecked")
        var messages = (List<Map<String, Object>>) ctx.get("delegation");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).get("sender")).isEqualTo("finance-agent");
        assertThat(messages.get(1).get("sender")).isEqualTo("home-agent");
    }

    @Test
    void gatherContext_respectsMessageLimit() {
        UUID caseId = UUID.randomUUID();
        provider = new LifeChannelContextProvider(channelService, messageStore, 5) {
            @Override protected Map<String, String> resolveActorChannels(UUID caseId) {
                return Map.of();
            }
        };

        provider.gatherContext(caseId);

        verify(messageStore, atLeastOnce()).scan(argThat(q -> q.limit() != null && q.limit() == 5));
    }

    @Test
    void gatherContext_serializationFormat() {
        UUID caseId = UUID.randomUUID();
        Instant t = Instant.parse("2026-07-07T12:00:00Z");
        Message msg = message("health-agent", MessageType.COMMAND, "Book appointment", t);

        when(messageStore.scan(argThat(q -> oversightChannelId.equals(q.channelId()))))
                .thenReturn(List.of(msg));

        Map<String, Object> result = provider.gatherContext(caseId);

        @SuppressWarnings("unchecked")
        var ctx = (Map<String, Object>) result.get("channelContext");
        @SuppressWarnings("unchecked")
        var messages = (List<Map<String, Object>>) ctx.get("oversight");
        assertThat(messages).hasSize(1);
        var m = messages.get(0);
        assertThat(m).containsKeys("sender", "type", "content", "createdAt");
        assertThat(m.get("sender")).isEqualTo("health-agent");
        assertThat(m.get("type")).isEqualTo("COMMAND");
        assertThat(m.get("content")).isEqualTo("Book appointment");
        assertThat(m.get("createdAt")).isEqualTo(t.toString());
    }

    // ── Helpers ──

    private static Channel channelWithId(UUID id) {
        return new Channel(id, "test", null, null, null, null, null,
                null, null, null, null, false, false, null, null, null);
    }

    private static Message message(String sender, MessageType type, String content, Instant createdAt) {
        return new Message(1L, UUID.randomUUID(), sender, type, ActorType.AGENT,
                "test-tenant", content, null, null, 0, null, null, null, null, null, 0, createdAt);
    }
}
