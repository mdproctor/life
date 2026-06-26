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
import io.casehub.life.app.engine.agent.AssessPatientResult;
import io.casehub.life.app.engine.agent.LifeOpenClawChatModelFactory;
import io.casehub.life.app.engine.agent.ProvideCareResult;
import io.casehub.api.model.AgentWorkerFunction;
import io.casehub.worker.api.Capability;
import io.casehub.worker.api.Worker;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;

/**
 * Care episode case hub — child case spawned by care-coordination via SubCase binding.
 *
 * <p>Not injected by {@link LifeCaseService} — only spawned as a sub-case by the
 * care-coordination case. Registered as a CDI bean so the engine can discover it.
 *
 * <p>Workers are lambda functions that run on Quartz worker threads. The humanTask
 * binding (record-notes) is defined in YAML and handled by
 * {@link io.casehub.workadapter.HumanTaskScheduleHandler} — no Java worker needed.
 *
 * <p>Refs casehub-life#6.
 */
@ApplicationScoped
public class CareEpisodeCaseHub extends YamlCaseHub {

    @Inject
    LifeOpenClawChatModelFactory openClawFactory;

    @ConfigProperty(name = "casehub.life.tenancy-id")
    String tenancyId;

    private volatile CaseDefinition augmentedDefinition;

    public CareEpisodeCaseHub() {
        super("life/care-episode.yaml");
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
                assessPatientWorker(),
                provideCareWorker()
        ));
        yaml.setAgentDescriptors(Map.of("openclaw:health-agent@1", healthDescriptor()));
        return yaml;
    }

    private static Capability cap(String name) {
        return Capability.builder().name(name).inputSchema(".").outputSchema(".").build();
    }

    /**
     * Assesses patient condition at start of care episode.
     *
     * <p>Uses OpenClaw's LLM API to assess patient condition including vital signs,
     * mobility status, and cognitive state.
     */
    private Worker assessPatientWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("health-agent"))
                .systemPrompt("""
                        You are a care episode agent. Assess patient condition including
                        vital signs, mobility status, and cognitive state.""")
                .responseSchema(AssessPatientResult.class)
                .build();

        return Worker.builder()
                .name("assess-patient-agent")
                .capabilities(List.of(cap("assess-patient")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    /**
     * Provides care based on patient assessment and care plan.
     *
     * <p>Uses OpenClaw's LLM API to provide care to the patient, completing
     * assigned tasks and recording observations.
     */
    private Worker provideCareWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("health-agent"))
                .systemPrompt("""
                        You are a care episode agent. Provide care to the patient, completing
                        assigned tasks and recording observations.""")
                .responseSchema(ProvideCareResult.class)
                .build();

        return Worker.builder()
                .name("provide-care-agent")
                .capabilities(List.of(cap("provide-care")))
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
