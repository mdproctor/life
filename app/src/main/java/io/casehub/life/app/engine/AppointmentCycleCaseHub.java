/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.life.app.engine;

import io.casehub.api.engine.YamlCaseHub;
import io.casehub.api.model.Capability;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.Worker;
import io.casehub.api.model.WorkerResult;
import io.casehub.api.model.ai.Agent;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.life.app.engine.agent.BookingResult;
import io.casehub.life.app.engine.agent.LifeOpenClawChatModelProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;

/**
 * Appointment cycle case hub — loads the YAML definition and augments it with workers.
 *
 * <p>book-appointment-agent is the first real LLM-backed worker (casehubio/life#25):
 * it uses WorkerFunction.AgentExec(Agent) with OpenClaw's /v1/chat/completions as the
 * LLM backend. Agent identity follows the {model-family}:{persona}@{major} convention
 * from docs/specs/life-actor-model.md. Refs engine#463 (settled function-as-worker design).
 *
 * <p>All other workers remain stubs (WorkerFunction.Sync lambdas) until full Layer 7 lands.
 *
 * <p>The humanTask binding (attend-and-record) is defined in YAML and handled by
 * {@link io.casehub.workadapter.HumanTaskScheduleHandler} — no Java worker needed.
 *
 * <p>augmentedDefinition is baked exactly once per JVM lifetime (double-checked lock).
 * chatModelProvider.get() is called once during Agent.build() in augment(). Config changes
 * to casehub.life.openclaw.* require a restart.
 */
@ApplicationScoped
public class AppointmentCycleCaseHub extends YamlCaseHub {

    @Inject
    LifeOpenClawChatModelProvider openClaw;

    @ConfigProperty(name = "casehub.life.tenancy-id")
    String tenancyId;

    private volatile CaseDefinition augmentedDefinition;

    public AppointmentCycleCaseHub() {
        super("life/appointment-cycle.yaml");
    }

    @Override
    public CaseDefinition getDefinition() {
        if (augmentedDefinition == null) {
            synchronized (this) {
                if (augmentedDefinition == null) {
                    augmentedDefinition = augment(super.getDefinition());
                }
            }
        }
        return augmentedDefinition;
    }

    private CaseDefinition augment(final CaseDefinition yaml) {
        yaml.getWorkers().addAll(List.of(
                bookAppointmentWorker(),
                findAlternativeWorker(),
                confirmAppointmentWorker(),
                preVisitPrepWorker(),
                recordHealthDecisionWorker()
        ));
        return yaml;
    }

    private static Capability cap(final String name) {
        return Capability.builder().name(name).inputSchema(".").outputSchema(".").build();
    }

    /**
     * Books an appointment via OpenClaw's LLM API (/v1/chat/completions).
     *
     * <p>First real LLM-backed worker in casehub-life. Uses WorkerFunction.AgentExec(Agent)
     * per the engine#463 settled abstraction. OpenClaw returns structured JSON conforming
     * to BookingResult — confirmed=false indicates a PENDING booking; the confirm-appointment
     * binding fires when .booking != null and .booking.declined != true.
     *
     * <p>AgentDescriptor.agentId "openclaw:health-agent@1" follows the
     * {model-family}:{persona}@{major} convention. This value MUST match the
     * casehub-openclaw-casehub config map key when WorkerProvisioner is wired (life#37).
     */
    private Worker bookAppointmentWorker() {
        final Agent bookingAgent = Agent.builder()
                .model(openClaw)
                .systemPrompt("""
                        You are a healthcare appointment booking agent for a UK household.
                        Book medical appointments with the requested provider.
                        If the provider is unavailable, set declined=true and provide a reason.
                        Respond with valid JSON only — no prose, no explanation.
                        """)
                .userMessage("Book a {{appointmentType}} appointment with provider {{provider}}.")
                .responseSchema(BookingResult.class)
                .build();

        final AgentDescriptor descriptor = new AgentDescriptor(
                "openclaw:health-agent@1",    // agentId — MUST match provisioner config key (life#37)
                "OpenClaw Health Agent",       // name
                "1",                           // version
                "openclaw",                    // provider
                "openclaw",                    // modelFamily
                null,                          // modelVersion — unknown
                null,                          // weightsFingerprint
                null,                          // domainVocabulary
                null,                          // slotVocabulary
                null,                          // dispositionVocabulary
                null,                          // axisVocabularies
                "casehubio/life/health",       // slot — matches scope path convention
                List.of(),                     // capabilities (populated when skill manifest available)
                null,                          // disposition
                "GB",                          // jurisdiction
                null,                          // dataHandlingPolicy
                tenancyId,                     // tenancyId — required, injected from config
                "Health domain booking and follow-up agent"  // briefing
        );

        return Worker.builder()
                .name("book-appointment-agent")
                .capabilities(List.of(cap("book-appointment")))
                .function(bookingAgent)
                .agentDescriptor(descriptor)
                .build();
    }

    /** Finds an alternative provider after a decline. Stub — pending Layer 7. */
    private static Worker findAlternativeWorker() {
        return Worker.builder()
                .name("find-alternative-agent")
                .capabilities(List.of(cap("find-alternative")))
                .function((Map<String, Object> input) -> WorkerResult.of(Map.of(
                        "alternativeFound", true,
                        "appointmentId", "APT-ALT-" + System.currentTimeMillis(),
                        "provider", "Dr Alternative",
                        "confirmed", false
                )))
                .build();
    }

    /** Sends appointment confirmation and reminder. Stub — pending Layer 7. */
    private static Worker confirmAppointmentWorker() {
        return Worker.builder()
                .name("confirm-appointment-agent")
                .capabilities(List.of(cap("confirm-appointment")))
                .function((Map<String, Object> input) -> WorkerResult.of(Map.of(
                        "confirmed", true,
                        "reminderSent", true
                )))
                .build();
    }

    /** Sends pre-visit checklist and preparation instructions. Stub — pending Layer 7. */
    private static Worker preVisitPrepWorker() {
        return Worker.builder()
                .name("pre-visit-prep-agent")
                .capabilities(List.of(cap("pre-visit-prep")))
                .function((Map<String, Object> input) -> WorkerResult.of(Map.of(
                        "checklistSent", true,
                        "instructions", "Bring ID, insurance card, list of medications"
                )))
                .build();
    }

    /** Records health decision to tamper-evident ledger. Stub — pending Layer 7. */
    private static Worker recordHealthDecisionWorker() {
        return Worker.builder()
                .name("record-health-decision-agent")
                .capabilities(List.of(cap("record-health-decision")))
                .function((Map<String, Object> input) -> WorkerResult.of(Map.of(
                        "recorded", true,
                        "ledgerEntryId", "LEDGER-" + System.currentTimeMillis()
                )))
                .build();
    }
}
