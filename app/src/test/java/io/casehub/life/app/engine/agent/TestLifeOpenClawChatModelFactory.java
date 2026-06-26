package io.casehub.life.app.engine.agent;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.casehub.api.model.ai.ChatModelProvider;
import io.casehub.api.model.ai.ModelType;
import io.casehub.openclaw.casehub.DirectCallBridge;
import io.casehub.openclaw.client.OpenClawClientConfig;
import io.casehub.openclaw.client.OpenClawHookClient;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.Map;

/**
 * Test replacement for {@link LifeOpenClawChatModelFactory}.
 *
 * <p>Returns a {@link ChatModel} that matches system prompt content against a canned
 * response map. No OpenClaw HTTP calls are made.
 *
 * <p>Registered in {@code quarkus.arc.selected-alternatives} in test config.
 *
 * <p>This is an {@code @Alternative} bean that extends the production factory. The
 * production factory constructor requires {@link DirectCallBridge}, {@link OpenClawHookClient},
 * and {@link OpenClawClientConfig} — beans which may not be available in tests because
 * the OpenClaw REST client (openclaw-gateway) points at a non-existent URL.
 * The test factory's protected no-arg constructor passes nulls to the super constructor —
 * it overrides {@link #forAgent(String)} entirely and never touches the bridge/hookClient.
 *
 * <p>RESPONSE map entries are keyed by system prompt substrings (case-insensitive).
 * Decline path: if the user message contains "unavailable" and the system prompt contains
 * "appointment booking", returns a declined booking response.
 *
 * <p>Refs casehubio/life#38 — Phase 2 test infrastructure.
 */
@Alternative
@Priority(10)
@ApplicationScoped
public class TestLifeOpenClawChatModelFactory extends LifeOpenClawChatModelFactory {

    private static final Map<String, String> RESPONSES = Map.ofEntries(
            // --- Health domain (health-agent) ---
            Map.entry("healthcare appointment booking",
                    "{\"appointmentId\":\"APT-MOCK\",\"provider\":\"Dr Smith\","
                    + "\"confirmed\":false,\"declined\":null,\"reason\":null}"),
            Map.entry("find an alternative",
                    "{\"alternativeFound\":true,\"appointmentId\":\"APT-ALT-MOCK\","
                    + "\"provider\":\"Dr Alternative\",\"confirmed\":false}"),
            Map.entry("send appointment confirmation",
                    "{\"confirmed\":true,\"reminderSent\":true}"),
            Map.entry("pre-visit preparation",
                    "{\"checklistSent\":true,\"instructions\":\"Bring ID, insurance card\"}"),
            Map.entry("record health decision",
                    "{\"recorded\":true,\"ledgerEntryId\":\"LEDGER-MOCK\"}"),
            Map.entry("assess care needs",
                    "{\"careLevel\":\"moderate\",\"recommendedFrequency\":\"weekly\","
                    + "\"specialRequirements\":[\"mobility support\"]}"),
            Map.entry("create a care plan",
                    "{\"schedule\":[\"Mon 9am\",\"Wed 2pm\"],\"duration\":\"2 hours\","
                    + "\"tasks\":[\"medication\",\"mobility exercises\"]}"),
            Map.entry("periodic health check",
                    "{\"reviewed\":true,\"healthConcern\":false,\"notes\":\"Stable condition\"}"),
            Map.entry("assess patient condition",
                    "{\"vitalSigns\":{\"bp\":\"120/80\",\"hr\":72,\"temp\":36.6},"
                    + "\"mobility\":\"assisted\",\"cognition\":\"alert\"}"),
            Map.entry("provide care",
                    "{\"tasksCompleted\":[\"medication\",\"mobility\"],\"duration\":\"90 min\","
                    + "\"observations\":\"Patient cooperative\"}"),

            // --- Home domain (home-agent) ---
            Map.entry("schedule a property inspection",
                    "{\"inspected\":true,\"condition\":\"good\",\"inspectionDate\":\"2026-07-01\"}"),
            Map.entry("gather contractor quotes",
                    "{\"quoteCount\":2,\"quotes\":[{\"contractor\":\"ABC\",\"amount\":500,"
                    + "\"available\":true},{\"contractor\":\"DEF\",\"amount\":650,\"available\":true}]}"),
            Map.entry("issue a commitment to the selected contractor",
                    "{\"commitmentIssued\":true,\"channel\":\"life/contractor/mock\"}"),
            Map.entry("monitor job progress",
                    "{\"progress\":\"75% complete\",\"estimatedCompletion\":\"2026-07-15\"}"),
            Map.entry("record job completion",
                    "{\"recorded\":true,\"ledgerEntryId\":\"LEDGER-MOCK\"}"),
            Map.entry("request a quote",
                    "{\"quoteRequested\":true,\"channel\":\"life/contractor/mock\","
                    + "\"deadlinePassed\":false}"),
            Map.entry("escalate an overdue",
                    "{\"escalated\":true,\"reminderSent\":true}"),
            Map.entry("process a received quote",
                    "{\"quoteAmount\":500,\"contractor\":\"ABC Plumbing\","
                    + "\"validUntil\":\"2026-07-30\"}"),
            Map.entry("monitor an active contractor job",
                    "{\"progress\":\"50% complete\",\"estimatedCompletion\":\"2026-07-20\"}"),
            Map.entry("record a contractor payment",
                    "{\"paymentRecorded\":true,\"amount\":500,\"ledgerEntryId\":\"LEDGER-MOCK\","
                    + "\"crossCaseSignal\":\"payment-complete\"}"),

            // --- Finance domain (finance-agent) ---
            Map.entry("gather financial data",
                    "{\"totalSpend\":5000,\"budgetLimit\":4500,"
                    + "\"categories\":[\"groceries\",\"utilities\",\"contractor\"]}"),
            Map.entry("analyse spending anomalies",
                    "{\"hasAnomalies\":true,\"anomalyDetails\":\"Spending exceeded budget by $500 (11%)\"}"),
            Map.entry("escalate anomalies",
                    "{\"escalationSent\":true,\"channel\":\"life/oversight\"}"),
            Map.entry("process oversight response",
                    "{\"approved\":true,\"comments\":\"Approved by household admin\"}"),
            Map.entry("produce a monthly financial report",
                    "{\"reportGenerated\":true,\"summary\":\"Within budget\","
                    + "\"ledgerEntryId\":\"LEDGER-MOCK\"}"),

            // --- Travel domain (travel-agent) ---
            Map.entry("research destination options",
                    "{\"options\":[{\"name\":\"Paris\",\"cost\":1200,\"rating\":\"4.5\"},"
                    + "{\"name\":\"Barcelona\",\"cost\":900,\"rating\":\"4.3\"}]}"),
            Map.entry("search for flights",
                    "{\"flights\":[{\"airline\":\"BA\",\"price\":450,\"stops\":0},"
                    + "{\"airline\":\"RY\",\"price\":280,\"stops\":1}]}"),
            Map.entry("search for hotels",
                    "{\"hotels\":[{\"name\":\"Grand Hotel\",\"price\":120,\"rating\":4.5},"
                    + "{\"name\":\"Budget Inn\",\"price\":60,\"rating\":3.0}]}"),
            Map.entry("assess the total travel budget",
                    "{\"totalCost\":3500,\"requiresApproval\":true,\"isHighValue\":false}"),
            Map.entry("book the selected flights and hotels",
                    "{\"bookingRef\":\"BK-MOCK\",\"status\":\"confirmed\","
                    + "\"declined\":null,\"reason\":null}"),
            Map.entry("rebook after a declined",
                    "{\"bookingRef\":\"BK-REBK-MOCK\",\"status\":\"confirmed\","
                    + "\"alternativeDates\":true}"),
            Map.entry("confirm the travel itinerary",
                    "{\"confirmed\":true,\"itinerarySent\":true,"
                    + "\"confirmationRef\":\"CONF-MOCK\"}")
    );

    @SuppressWarnings("unused")
    protected TestLifeOpenClawChatModelFactory() {
        super();
    }

    @Override
    public ChatModelProvider forAgent(String openClawAgentId) {
        return new ChatModelProvider() {
            @Override
            public ModelType type() {
                return ModelType.OPENAI;
            }

            @Override
            public ChatModel get() {
                return new TestChatModel();
            }
        };
    }

    private static final class TestChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            String sysPrompt = request.messages().stream()
                    .filter(m -> m instanceof SystemMessage)
                    .map(m -> ((SystemMessage) m).text().toLowerCase())
                    .findFirst()
                    .orElse("");

            // Match decline path for appointment booking
            boolean decline = request.messages().stream()
                    .filter(m -> m instanceof dev.langchain4j.data.message.UserMessage)
                    .map(m -> ((dev.langchain4j.data.message.UserMessage) m).singleText())
                    .findFirst()
                    .map(t -> t.toLowerCase().contains("unavailable"))
                    .orElse(false);
            if (decline && sysPrompt.contains("appointment booking")) {
                return respond("{\"appointmentId\":null,\"provider\":\"Dr Gone\","
                        + "\"confirmed\":false,\"declined\":true,"
                        + "\"reason\":\"Provider unavailable\"}");
            }

            for (var entry : RESPONSES.entrySet()) {
                if (sysPrompt.contains(entry.getKey())) {
                    return respond(entry.getValue());
                }
            }

            return respond("{\"ok\":true}");
        }

        private static ChatResponse respond(String json) {
            return ChatResponse.builder()
                    .aiMessage(new AiMessage(json))
                    .build();
        }
    }
}
