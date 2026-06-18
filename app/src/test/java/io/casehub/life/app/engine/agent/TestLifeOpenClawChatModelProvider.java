package io.casehub.life.app.engine.agent;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.casehub.api.model.ai.ModelType;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/**
 * Test-only CDI alternative for LifeOpenClawChatModelProvider.
 *
 * <p>Activated via quarkus.arc.selected-alternatives in test/resources/application.properties.
 * Avoids @InjectMock (which causes Quarkus to restart between test classes, re-registering
 * codecs and failing subsequent tests).
 *
 * <p>The ChatModel returned is request-aware: it detects "unavailable" in the rendered user
 * message ("Book a {{appointmentType}} appointment with provider {{provider}}.") and returns
 * the decline JSON. All other requests return the success JSON. This covers:
 * - AppointmentCycleCaseHubTest (structural tests that call getDefinition())
 * - AppointmentCycleIntegrationTest (case-execution tests including declinePath)
 *
 * <p>chatModelProvider.get() is called ONCE per JVM lifetime (baked into Agent during
 * AppointmentCycleCaseHub.augment()). The returned ChatModel handles all request variants.
 */
@Alternative
@Priority(10)
@ApplicationScoped
public class TestLifeOpenClawChatModelProvider extends LifeOpenClawChatModelProvider {

    @Override
    public ModelType type() {
        return ModelType.OPENAI;
    }

    @Override
    public ChatModel get() {
        return new RequestAwareChatModel();
    }

    private static final class RequestAwareChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(final ChatRequest request) {
            final boolean decline = request.messages().stream()
                    .filter(m -> m instanceof UserMessage)
                    .map(m -> ((UserMessage) m).singleText())
                    .findFirst()
                    .map(t -> t.toLowerCase().contains("unavailable"))
                    .orElse(false);

            final String json = decline
                    ? "{\"appointmentId\":null,\"provider\":\"Dr Gone\","
                      + "\"confirmed\":false,\"declined\":true,\"reason\":\"Provider unavailable\"}"
                    : "{\"appointmentId\":\"APT-MOCK\",\"provider\":\"Dr Smith\","
                      + "\"confirmed\":false,\"declined\":null,\"reason\":null}";

            return ChatResponse.builder()
                    .aiMessage(new AiMessage(json))
                    .build();
        }
    }
}
