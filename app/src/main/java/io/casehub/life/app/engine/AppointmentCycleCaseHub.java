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
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ai.Agent;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.life.app.engine.agent.BookingResult;
import io.casehub.life.app.engine.agent.ConfirmAppointmentResult;
import io.casehub.life.app.engine.agent.FindAlternativeResult;
import io.casehub.life.app.engine.agent.LifeOpenClawChatModelFactory;
import io.casehub.life.app.engine.agent.PreVisitPrepResult;
import io.casehub.life.app.engine.agent.RecordHealthDecisionResult;
import io.casehub.api.model.AgentWorkerFunction;
import io.casehub.worker.api.Capability;
import io.casehub.worker.api.Worker;
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
    LifeOpenClawChatModelFactory openClawFactory;

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

        yaml.getWorkers().addAll(List.of(
                bookAppointmentWorker(),
                findAlternativeWorker(),
                confirmAppointmentWorker(),
                preVisitPrepWorker(),
                recordHealthDecisionWorker()
        ));
        yaml.setAgentDescriptors(Map.of("openclaw:health-agent@1", descriptor));
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
                .model(openClawFactory.forAgent("health-agent"))
                .systemPrompt("""
                        You are a healthcare appointment booking agent for a UK household.
                        Book medical appointments with the requested provider.
                        If the provider is unavailable, set declined=true and provide a reason.
                        Respond with valid JSON only — no prose, no explanation.
                        """)
                .userMessage("Book a {{appointmentType}} appointment with provider {{provider}}.")
                .responseSchema(BookingResult.class)
                .build();

        return Worker.builder()
                .name("book-appointment-agent")
                .capabilities(List.of(cap("book-appointment")))
                .function(new AgentWorkerFunction(bookingAgent))
                .build();
    }

    /**
     * Finds an alternative provider after a booking was declined.
     *
     * <p>Uses OpenClaw's LLM API to search for alternative providers and propose
     * an alternative appointment. Part of the appointment-cycle adaptive path.
     */
    private Worker findAlternativeWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("health-agent"))
                .systemPrompt("""
                        You are a healthcare appointment agent. Find an alternative provider
                        after a booking was declined. Search available providers and propose
                        an alternative appointment.""")
                .responseSchema(FindAlternativeResult.class)
                .build();

        return Worker.builder()
                .name("find-alternative-agent")
                .capabilities(List.of(cap("find-alternative")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    /**
     * Sends appointment confirmation and schedules a reminder.
     *
     * <p>Uses OpenClaw's LLM API to send confirmation to the patient and schedule
     * a reminder for 24 hours before the appointment.
     */
    private Worker confirmAppointmentWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("health-agent"))
                .systemPrompt("""
                        You are a healthcare appointment agent. Send appointment confirmation
                        to the patient and schedule a reminder for 24 hours before.""")
                .responseSchema(ConfirmAppointmentResult.class)
                .build();

        return Worker.builder()
                .name("confirm-appointment-agent")
                .capabilities(List.of(cap("confirm-appointment")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    /**
     * Sends pre-visit preparation checklist and instructions.
     *
     * <p>Uses OpenClaw's LLM API to send a pre-visit checklist and preparation
     * instructions to the patient.
     */
    private Worker preVisitPrepWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("health-agent"))
                .systemPrompt("""
                        You are a healthcare appointment agent. Send pre-visit preparation
                        checklist and instructions to the patient.""")
                .responseSchema(PreVisitPrepResult.class)
                .build();

        return Worker.builder()
                .name("pre-visit-prep-agent")
                .capabilities(List.of(cap("pre-visit-prep")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    /**
     * Records health decision outcome to the tamper-evident ledger.
     *
     * <p>Uses OpenClaw's LLM API to record health decision outcomes to the
     * tamper-evident ledger for compliance tracking.
     */
    private Worker recordHealthDecisionWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("health-agent"))
                .systemPrompt("""
                        You are a healthcare records agent. Record health decision outcomes
                        to the tamper-evident ledger.""")
                .responseSchema(RecordHealthDecisionResult.class)
                .build();

        return Worker.builder()
                .name("record-health-decision-agent")
                .capabilities(List.of(cap("record-health-decision")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }
}
