package io.casehub.life.app.engine.agent;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.casehub.api.model.ai.Agent;
import io.casehub.api.model.ai.ChatModelProvider;
import io.casehub.api.model.ai.ModelType;
import io.casehub.worker.api.WorkerResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// AiMessage and ChatResponse have public constructors/builders and must NOT be mocked
// (their methods are final in LangChain4J 1.14.1 — Mockito cannot intercept them).

/**
 * Unit test for the booking agent's LLM integration.
 *
 * <p>No Quarkus — tests Agent.execute() directly via a ChatModelProvider stub.
 * Verifies structured output parsing for both the booking path and the decline path.
 */
class LifeOpenClawAgentTest {

    private ChatModel mockModel;
    private ChatModelProvider stubProvider;

    @BeforeEach
    void setup() {
        mockModel = mock(ChatModel.class);
        stubProvider = new ChatModelProvider() {
            @Override
            public ModelType type() {
                return ModelType.OPENAI;
            }

            @Override
            public ChatModel get() {
                return mockModel;
            }
        };
    }

    private static ChatResponse stubResponse(final String json) {
        return ChatResponse.builder()
                .aiMessage(new AiMessage(json))
                .build();
    }

    private Agent bookingAgent() {
        return Agent.builder()
                .model(stubProvider)
                .systemPrompt("""
                        You are a healthcare appointment booking agent for a UK household.
                        Book medical appointments with the requested provider.
                        If the provider is unavailable, set declined=true and provide a reason.
                        Respond with valid JSON only — no prose, no explanation.
                        """)
                .userMessage("Book a {{appointmentType}} appointment with provider {{provider}}.")
                .responseSchema(BookingResult.class)
                .build();
    }

    @Test
    void execute_booking_returnsPendingAppointment() {
        // confirmed=false is correct: booking step returns PENDING.
        // confirmed=true is set by the later confirm-appointment binding.
        when(mockModel.chat(any(ChatRequest.class))).thenReturn(stubResponse(
                "{\"appointmentId\":\"APT-123\",\"provider\":\"Dr Smith\","
                + "\"confirmed\":false,\"declined\":null,\"reason\":null}"));

        final WorkerResult result = bookingAgent().execute(
                Map.of("appointmentType", "GP checkup", "provider", "Dr Smith"));

        assertThat(result.output().get("appointmentId")).isEqualTo("APT-123");
        assertThat(result.output().get("provider")).isEqualTo("Dr Smith");
        assertThat(result.output().get("confirmed")).isEqualTo(false);
        assertThat(result.output().get("declined")).isNull();
    }

    @Test
    void execute_unavailableProvider_returnsDeclined() {
        when(mockModel.chat(any(ChatRequest.class))).thenReturn(stubResponse(
                "{\"appointmentId\":null,\"provider\":\"Dr Gone\","
                + "\"confirmed\":false,\"declined\":true,\"reason\":\"Not accepting new patients\"}"));

        final WorkerResult result = bookingAgent().execute(
                Map.of("appointmentType", "GP checkup", "provider", "Dr Gone"));

        assertThat(result.output().get("declined")).isEqualTo(true);
        assertThat(result.output().get("reason")).isEqualTo("Not accepting new patients");
        assertThat(result.output().get("appointmentId")).isNull();
    }

    @Test
    void execute_userMessageTemplate_containsProviderName() {
        // Verifies the user message template is rendered — the rendered text is
        // what the request-aware mock in integration tests must detect.
        // Template: "Book a {{appointmentType}} appointment with provider {{provider}}."
        // With provider="unavailable" → "Book a GP appointment with provider unavailable."
        when(mockModel.chat(any(ChatRequest.class))).thenAnswer(invocation -> {
            final ChatRequest req = invocation.getArgument(0);
            final String userText = req.messages().stream()
                    .filter(m -> m instanceof UserMessage)
                    .map(m -> ((UserMessage) m).singleText())
                    .findFirst().orElse("");
            assertThat(userText).contains("Dr Smith");
            assertThat(userText).contains("GP checkup");
            return stubResponse("{\"appointmentId\":\"APT-999\",\"provider\":\"Dr Smith\","
                    + "\"confirmed\":false,\"declined\":null,\"reason\":null}");
        });

        bookingAgent().execute(Map.of("appointmentType", "GP checkup", "provider", "Dr Smith"));
    }
}
