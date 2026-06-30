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
import io.casehub.life.app.engine.agent.AssessPatientResult;
import io.casehub.life.app.engine.agent.LifeAgentDescriptorFactory;
import io.casehub.life.app.engine.agent.LifeOpenClawChatModelFactory;
import io.casehub.life.app.engine.agent.ProvideCareResult;
import io.casehub.api.model.AgentWorkerFunction;
import io.casehub.worker.api.Worker;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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

    private static final LifeAgent AGENT = LifeAgent.HEALTH;

    @Inject
    LifeOpenClawChatModelFactory openClawFactory;

    @Inject
    LifeAgentDescriptorFactory descriptorFactory;

    public CareEpisodeCaseHub() {
        super("life/care-episode.yaml");
    }

    @Override
    protected void augment(CaseDefinition definition) {
        definition.getWorkers().addAll(List.of(
                assessPatientWorker(),
                provideCareWorker()
        ));
        definition.setAgentDescriptors(Map.of(
                AGENT.agentId(), descriptorFactory.descriptorFor(AGENT)));
    }

    /**
     * Assesses patient condition at start of care episode.
     *
     * <p>Uses OpenClaw's LLM API to assess patient condition including vital signs,
     * mobility status, and cognitive state.
     */
    private Worker assessPatientWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent(AGENT))
                .systemPrompt("""
                        You are a care episode agent. Assess patient condition including
                        vital signs, mobility status, and cognitive state.""")
                .responseSchema(AssessPatientResult.class)
                .build();

        return Worker.builder()
                .name("assess-patient-agent")
                .capabilityName("assess-patient")
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
                .model(openClawFactory.forAgent(AGENT))
                .systemPrompt("""
                        You are a care episode agent. Provide care to the patient, completing
                        assigned tasks and recording observations.""")
                .responseSchema(ProvideCareResult.class)
                .build();

        return Worker.builder()
                .name("provide-care-agent")
                .capabilityName("provide-care")
                .function(new AgentWorkerFunction(agent))
                .build();
    }
}
