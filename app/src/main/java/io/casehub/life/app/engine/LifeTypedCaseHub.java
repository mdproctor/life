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
import io.casehub.api.model.AgentWorkerFunction;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ai.Agent;
import io.casehub.life.api.LifeCaseType;
import io.casehub.life.app.cbr.CbrInputTransformer;
import io.casehub.life.app.cbr.LifeCbrExperienceFormatter;
import io.casehub.life.app.engine.agent.LifeAgentDescriptorFactory;
import io.casehub.life.app.engine.agent.LifeOpenClawChatModelFactory;
import io.casehub.worker.api.Worker;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * Template method base class for Life case definitions backed by YAML and OpenClaw agents.
 * Subclasses override {@link #configureCase(CaseDefinition)} to add workers and bindings.
 * AgentDescriptor registration is handled automatically by {@link #augment(CaseDefinition)}.
 * Use {@link #agentWorker(String, String, Class)} for standard LLM-backed workers.
 */
public abstract class LifeTypedCaseHub extends YamlCaseHub {

    static final  String              CBR_SYSTEM_PROMPT_SUFFIX = "If a _cbrContext section is present in the input, it contains summaries of similar past cases. Use these to calibrate your response — adjust cost estimates, timeline predictions, and risk assessments based on historical patterns. If no _cbrContext is present, proceed with your best judgment.";
    private final LifeAgent           agent;
    @Inject
    LifeOpenClawChatModelFactory openClawFactory;
    @Inject
    LifeAgentDescriptorFactory descriptorFactory;
    @Inject
    LifeCbrExperienceFormatter cbrFormatter;
    @Inject
    com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    CbrInputTransformer cbrInputTransformer;


    protected LifeTypedCaseHub(String path, LifeAgent agent) {
        super(path);
        this.agent = agent;
    }

    public abstract LifeCaseType lifeCaseType();

    protected LifeAgent agent() {
        return agent;
    }

    @Override
    protected final void augment(CaseDefinition definition) {
        configureCase(definition);
        definition.setAgentDescriptors(Map.of(
                agent.agentId(), descriptorFactory.descriptorFor(agent)));
    }

    protected abstract void configureCase(CaseDefinition definition);

    protected Worker agentWorker(String capabilityName, String systemPrompt,
                                 Class<?> responseSchema) {
        Agent a = Agent.builder()
                       .model(openClawFactory.forAgent(agent))
                       .systemPrompt(systemPrompt + "\n\n" + CBR_SYSTEM_PROMPT_SUFFIX)
                       .inputTransformer(cbrInputTransformer)
                       .responseSchema(responseSchema)
                       .build();
        return Worker.builder()
                     .name(capabilityName + "-agent")
                     .capabilityName(capabilityName)
                     .function(new AgentWorkerFunction(a))
                     .build();
    }

    @jakarta.annotation.PostConstruct
    void initCbrTransformer() {
        this.cbrInputTransformer = new CbrInputTransformer(cbrFormatter, objectMapper);
    }
}

