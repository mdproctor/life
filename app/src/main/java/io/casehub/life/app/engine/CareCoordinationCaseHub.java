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
import io.casehub.life.app.engine.agent.CarePlanResult;
import io.casehub.life.app.engine.agent.HealthCheckResult;
import io.casehub.life.app.engine.agent.LifeOpenClawChatModelFactory;
import io.casehub.life.app.engine.agent.NeedsAssessmentResult;
import io.casehub.api.model.AgentWorkerFunction;
import io.casehub.worker.api.Capability;
import io.casehub.worker.api.Worker;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;

/**
 * Care coordination case hub — loads the YAML definition and augments it with
 * in-process worker functions.
 *
 * <p>Workers are lambda functions that run on Quartz worker threads. The three humanTask
 * bindings (assign-carer, escalate-concern, care-review) are defined in YAML and handled by
 * {@link io.casehub.workadapter.HumanTaskScheduleHandler} — no Java worker needed.
 *
 * <p>SubCase pattern: the care-episode binding spawns a child case (care-episode) and
 * waits for completion. The child's final context is merged back as {@code episodeResult}.
 *
 * <p>Adaptive escalation: the escalate-concern binding fires only when
 * {@code .healthCheck.healthConcern == true} — otherwise the workflow proceeds
 * directly to care-review. In production, the escalation worker would also signal
 * an active appointment-cycle case via CaseHubRuntime.signal(). Refs casehub-life#6.
 */
@ApplicationScoped
public class CareCoordinationCaseHub extends YamlCaseHub {

    @Inject
    LifeOpenClawChatModelFactory openClawFactory;

    @ConfigProperty(name = "casehub.life.tenancy-id")
    String tenancyId;

    private volatile CaseDefinition augmentedDefinition;

    public CareCoordinationCaseHub() {
        super("life/care-coordination.yaml");
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

    private CaseDefinition augment(CaseDefinition yaml) {
        yaml.getWorkers().addAll(List.of(
                needsAssessmentWorker(),
                carePlanWorker(),
                healthCheckWorker()
        ));
        yaml.setAgentDescriptors(Map.of("openclaw:health-agent@1", healthDescriptor()));
        return yaml;
    }

    private static Capability cap(String name) {
        return Capability.builder().name(name).inputSchema(".").outputSchema(".").build();
    }

    /**
     * Assesses care needs based on the care request.
     *
     * <p>Uses OpenClaw's LLM API to assess patient care needs, determining
     * care level, recommended frequency, and any special requirements.
     */
    private Worker needsAssessmentWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("health-agent"))
                .systemPrompt("""
                        You are a care coordination agent. Assess care needs for the patient,
                        determining care level, recommended frequency, and any special requirements.""")
                .responseSchema(NeedsAssessmentResult.class)
                .build();

        return Worker.builder()
                .name("needs-assessment-agent")
                .capabilities(List.of(cap("needs-assessment")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    /**
     * Creates a care plan with schedule and task list.
     *
     * <p>Uses OpenClaw's LLM API to create a care plan with schedule, duration,
     * and task list based on the needs assessment.
     */
    private Worker carePlanWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("health-agent"))
                .systemPrompt("""
                        You are a care coordination agent. Create a care plan with schedule,
                        duration, and task list based on the needs assessment.""")
                .responseSchema(CarePlanResult.class)
                .build();

        return Worker.builder()
                .name("care-plan-agent")
                .capabilities(List.of(cap("care-plan")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    /**
     * Performs periodic health check and flags concerns.
     *
     * <p>Uses OpenClaw's LLM API to perform a periodic health check, reviewing
     * the patient's condition and flagging any concerns for escalation.
     */
    private Worker healthCheckWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("health-agent"))
                .systemPrompt("""
                        You are a care coordination agent. Perform a periodic health check,
                        reviewing the patient's condition and flagging any concerns.""")
                .responseSchema(HealthCheckResult.class)
                .build();

        return Worker.builder()
                .name("health-check-agent")
                .capabilities(List.of(cap("health-check")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    private AgentDescriptor healthDescriptor() {
        return new AgentDescriptor(
                "openclaw:health-agent@1",       // agentId
                "OpenClaw Health Agent",         // name
                "1",                             // version
                "openclaw",                      // provider
                "openclaw",                      // modelFamily
                null,                            // modelVersion
                null,                            // weightsFingerprint
                null,                            // domainVocabulary
                null,                            // slotVocabulary
                null,                            // dispositionVocabulary
                null,                            // axisVocabularies
                "casehubio/life/health",         // slot
                List.of(),                       // capabilities
                null,                            // disposition
                "GB",                            // jurisdiction
                null,                            // dataHandlingPolicy
                tenancyId,                       // tenancyId
                "Health domain agent"            // briefing
        );
    }
}
