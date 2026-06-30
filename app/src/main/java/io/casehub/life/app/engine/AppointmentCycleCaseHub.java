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

import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ai.Agent;
import io.casehub.life.api.LifeCaseType;
import io.casehub.life.app.engine.agent.BookingResult;
import io.casehub.life.app.engine.agent.ConfirmAppointmentResult;
import io.casehub.life.app.engine.agent.FindAlternativeResult;
import io.casehub.life.app.engine.agent.PreVisitPrepResult;
import io.casehub.life.app.engine.agent.RecordHealthDecisionResult;
import io.casehub.api.model.AgentWorkerFunction;
import io.casehub.worker.api.Worker;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Appointment cycle case hub — loads the YAML definition and augments it with workers.
 *
 * <p>book-appointment-agent is the first real LLM-backed worker (casehubio/life#25):
 * it uses WorkerFunction.AgentExec(Agent) with OpenClaw's /v1/chat/completions as the
 * LLM backend. Agent identity follows the {model-family}:{persona}@{major} convention
 * from docs/specs/life-actor-model.md. Refs engine#463 (settled function-as-worker design).
 *
 * <p>The humanTask binding (attend-and-record) is defined in YAML and handled by
 * {@link io.casehub.workadapter.HumanTaskScheduleHandler} — no Java worker needed.
 */
@ApplicationScoped
public class AppointmentCycleCaseHub extends LifeTypedCaseHub {

    public AppointmentCycleCaseHub() {
        super("life/appointment-cycle.yaml", LifeAgent.HEALTH);
    }

    @Override
    public LifeCaseType lifeCaseType() {
        return LifeCaseType.APPOINTMENT_CYCLE;
    }

    @Override
    protected void configureCase(CaseDefinition definition) {
        definition.getWorkers().add(bookAppointmentWorker());
        definition.getWorkers().add(agentWorker("find-alternative", """
                You are a healthcare appointment agent. Find an alternative provider
                after a booking was declined. Search available providers and propose
                an alternative appointment.""", FindAlternativeResult.class));
        definition.getWorkers().add(agentWorker("confirm-appointment", """
                You are a healthcare appointment agent. Send appointment confirmation
                to the patient and schedule a reminder for 24 hours before.""", ConfirmAppointmentResult.class));
        definition.getWorkers().add(agentWorker("pre-visit-prep", """
                You are a healthcare appointment agent. Send pre-visit preparation
                checklist and instructions to the patient.""", PreVisitPrepResult.class));
        definition.getWorkers().add(agentWorker("record-health-decision", """
                You are a healthcare records agent. Record health decision outcomes
                to the tamper-evident ledger.""", RecordHealthDecisionResult.class));
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
                .model(openClawFactory.forAgent(agent()))
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
                .capabilityName("book-appointment")
                .function(new AgentWorkerFunction(bookingAgent))
                .build();
    }
}
