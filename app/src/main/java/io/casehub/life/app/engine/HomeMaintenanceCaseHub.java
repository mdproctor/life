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
import io.casehub.life.app.engine.agent.GetQuotesResult;
import io.casehub.life.app.engine.agent.IssueCommitmentResult;
import io.casehub.life.app.engine.agent.LifeOpenClawChatModelFactory;
import io.casehub.life.app.engine.agent.MonitorJobResult;
import io.casehub.life.app.engine.agent.RecordCompletionResult;
import io.casehub.life.app.engine.agent.ScheduleInspectionResult;
import io.casehub.api.model.AgentWorkerFunction;
import io.casehub.worker.api.Capability;
import io.casehub.worker.api.Worker;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;

/**
 * Home maintenance cycle case hub — loads the YAML definition and augments it
 * with in-process worker functions.
 *
 * <p>Workers are lambda functions that run on Quartz worker threads. The two humanTask
 * bindings (approve-contractor, verify-completion) are defined in YAML and handled by
 * {@link io.casehub.workadapter.HumanTaskScheduleHandler} — no Java worker needed.
 *
 * <p>Qhorus bridge pattern: the issue-commitment worker is a STUB — in production it would
 * create a qhorus COMMAND on a case-specific channel. The monitor-job binding fires when
 * {@code QhorusMessageSignalBridge} sets {@code .channelMessage} with
 * {@code messageType == "RESPONSE"}. Refs casehub-life#6.
 */
@ApplicationScoped
public class HomeMaintenanceCaseHub extends YamlCaseHub {

    @Inject
    LifeOpenClawChatModelFactory openClawFactory;

    @ConfigProperty(name = "casehub.life.tenancy-id")
    String tenancyId;

    private volatile CaseDefinition augmentedDefinition;

    public HomeMaintenanceCaseHub() {
        super("life/home-maintenance.yaml");
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
                scheduleInspectionWorker(),
                getQuotesWorker(),
                issueCommitmentWorker(),
                monitorJobWorker(),
                recordCompletionWorker()
        ));
        yaml.setAgentDescriptors(Map.of("openclaw:home-agent@1", homeDescriptor()));
        return yaml;
    }

    private static Capability cap(String name) {
        return Capability.builder().name(name).inputSchema(".").outputSchema(".").build();
    }

    /**
     * Schedules and performs a home inspection.
     *
     * <p>Uses OpenClaw's LLM API to schedule a property inspection, assess
     * the condition, and report findings.
     */
    private Worker scheduleInspectionWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("home-agent"))
                .systemPrompt("""
                        You are a home maintenance agent. Schedule a property inspection,
                        assess the condition, and report findings.""")
                .responseSchema(ScheduleInspectionResult.class)
                .build();

        return Worker.builder()
                .name("schedule-inspection-agent")
                .capabilities(List.of(cap("schedule-inspection")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    /**
     * Obtains contractor quotes based on inspection results.
     *
     * <p>Uses OpenClaw's LLM API to gather contractor quotes for the
     * required maintenance work.
     */
    private Worker getQuotesWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("home-agent"))
                .systemPrompt("""
                        You are a home maintenance agent. Gather contractor quotes for the
                        required maintenance work.""")
                .responseSchema(GetQuotesResult.class)
                .build();

        return Worker.builder()
                .name("get-quotes-agent")
                .capabilities(List.of(cap("get-quotes")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    /**
     * Issues a commitment to the selected contractor.
     *
     * <p>Uses OpenClaw's LLM API to issue a commitment to the selected
     * contractor for the approved work.
     */
    private Worker issueCommitmentWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("home-agent"))
                .systemPrompt("""
                        You are a home maintenance agent. Issue a commitment to the selected
                        contractor for the approved work.""")
                .responseSchema(IssueCommitmentResult.class)
                .build();

        return Worker.builder()
                .name("issue-commitment-agent")
                .capabilities(List.of(cap("issue-commitment")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    /**
     * Monitors job progress after contractor RESPONSE received.
     *
     * <p>Uses OpenClaw's LLM API to monitor job progress and report
     * estimated completion.
     */
    private Worker monitorJobWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("home-agent"))
                .systemPrompt("""
                        You are a home maintenance agent. Monitor job progress and report
                        estimated completion.""")
                .responseSchema(MonitorJobResult.class)
                .build();

        return Worker.builder()
                .name("monitor-job-agent")
                .capabilities(List.of(cap("monitor-job")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    /**
     * Records job completion to tamper-evident ledger.
     *
     * <p>Uses OpenClaw's LLM API to record job completion to the
     * tamper-evident ledger.
     */
    private Worker recordCompletionWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("home-agent"))
                .systemPrompt("""
                        You are a home maintenance agent. Record job completion to the
                        tamper-evident ledger.""")
                .responseSchema(RecordCompletionResult.class)
                .build();

        return Worker.builder()
                .name("record-completion-agent")
                .capabilities(List.of(cap("record-completion")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    private AgentDescriptor homeDescriptor() {
        return new AgentDescriptor(
                "openclaw:home-agent@1",       // agentId
                "OpenClaw Home Agent",         // name
                "1",                           // version
                "openclaw",                    // provider
                "openclaw",                    // modelFamily
                null,                          // modelVersion
                null,                          // weightsFingerprint
                null,                          // domainVocabulary
                null,                          // slotVocabulary
                null,                          // dispositionVocabulary
                null,                          // axisVocabularies
                "casehubio/life/household",    // slot
                List.of(),                     // capabilities
                null,                          // disposition
                "GB",                          // jurisdiction
                null,                          // dataHandlingPolicy
                tenancyId,                     // tenancyId
                "Household maintenance agent"  // briefing
        );
    }
}
