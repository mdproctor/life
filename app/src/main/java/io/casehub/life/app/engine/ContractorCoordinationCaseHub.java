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
import io.casehub.life.app.engine.agent.JobMonitoringResult;
import io.casehub.life.app.engine.agent.LifeOpenClawChatModelFactory;
import io.casehub.life.app.engine.agent.QuoteReceivedResult;
import io.casehub.life.app.engine.agent.RecordPaymentResult;
import io.casehub.life.app.engine.agent.RequestQuoteResult;
import io.casehub.life.app.engine.agent.WatchdogEscalationResult;
import io.casehub.api.model.AgentWorkerFunction;
import io.casehub.worker.api.Capability;
import io.casehub.worker.api.Worker;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;

/**
 * Contractor coordination case hub — loads the YAML definition and augments it
 * with in-process worker functions.
 *
 * <p>Workers are lambda functions that run on Quartz worker threads. The two humanTask
 * bindings (approve-quote, payment-gate) are defined in YAML and handled by
 * {@link io.casehub.workadapter.HumanTaskScheduleHandler} — no Java worker needed.
 *
 * <p>Full qhorus lifecycle: the request-quote worker issues a COMMAND on a case-specific
 * channel. The watchdog-escalation binding is adaptive — fires only when
 * {@code .quoteRequest.deadlinePassed == true}. The quote-received binding fires when
 * {@code QhorusMessageSignalBridge} sets {@code .channelMessage} with
 * {@code messageType == "RESPONSE"}.
 *
 * <p>Cross-case signal: the record-payment worker captures the intent to signal active
 * financial-review cases. In production it would call {@code LifeLedgerWriter} and
 * {@code CaseHubRuntime.signal()}. Refs casehub-life#6.
 */
@ApplicationScoped
public class ContractorCoordinationCaseHub extends YamlCaseHub {

    @Inject
    LifeOpenClawChatModelFactory openClawFactory;

    @ConfigProperty(name = "casehub.life.tenancy-id")
    String tenancyId;

    private volatile CaseDefinition augmentedDefinition;

    public ContractorCoordinationCaseHub() {
        super("life/contractor-coordination.yaml");
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
                requestQuoteWorker(),
                watchdogEscalationWorker(),
                quoteReceivedWorker(),
                jobMonitoringWorker(),
                recordPaymentWorker()
        ));
        yaml.setAgentDescriptors(Map.of("openclaw:home-agent@1", homeDescriptor()));
        return yaml;
    }

    private static Capability cap(String name) {
        return Capability.builder().name(name).inputSchema(".").outputSchema(".").build();
    }

    /**
     * Requests a quote from the contractor.
     *
     * <p>Uses OpenClaw's LLM API to request a quote from the contractor via
     * the appropriate messaging channel.
     */
    private Worker requestQuoteWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("home-agent"))
                .systemPrompt("""
                        You are a contractor coordination agent. Request a quote from the
                        contractor via the appropriate messaging channel.""")
                .responseSchema(RequestQuoteResult.class)
                .build();

        return Worker.builder()
                .name("request-quote-agent")
                .capabilities(List.of(cap("request-quote")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    /**
     * Escalates an overdue contractor commitment.
     *
     * <p>Uses OpenClaw's LLM API to escalate an overdue contractor commitment
     * by sending a reminder.
     */
    private Worker watchdogEscalationWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("home-agent"))
                .systemPrompt("""
                        You are a contractor coordination agent. Escalate an overdue
                        contractor commitment by sending a reminder.""")
                .responseSchema(WatchdogEscalationResult.class)
                .build();

        return Worker.builder()
                .name("watchdog-escalation-agent")
                .capabilities(List.of(cap("watchdog-escalation")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    /**
     * Processes a received quote from the contractor.
     *
     * <p>Uses OpenClaw's LLM API to process a received quote, extracting amount,
     * contractor details, and validity period.
     */
    private Worker quoteReceivedWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("home-agent"))
                .systemPrompt("""
                        You are a contractor coordination agent. Process a received quote,
                        extracting amount, contractor details, and validity period.""")
                .responseSchema(QuoteReceivedResult.class)
                .build();

        return Worker.builder()
                .name("quote-received-agent")
                .capabilities(List.of(cap("quote-received")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    /**
     * Monitors an active contractor job.
     *
     * <p>Uses OpenClaw's LLM API to monitor an active contractor job and
     * report progress.
     */
    private Worker jobMonitoringWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("home-agent"))
                .systemPrompt("""
                        You are a contractor coordination agent. Monitor an active contractor
                        job and report progress.""")
                .responseSchema(JobMonitoringResult.class)
                .build();

        return Worker.builder()
                .name("job-monitoring-agent")
                .capabilities(List.of(cap("job-monitoring")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    /**
     * Records a contractor payment to the tamper-evident ledger.
     *
     * <p>Uses OpenClaw's LLM API to record a contractor payment to the
     * tamper-evident ledger and emit a cross-case signal.
     */
    private Worker recordPaymentWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("home-agent"))
                .systemPrompt("""
                        You are a contractor coordination agent. Record a contractor payment
                        to the tamper-evident ledger and emit a cross-case signal.""")
                .responseSchema(RecordPaymentResult.class)
                .build();

        return Worker.builder()
                .name("record-payment-agent")
                .capabilities(List.of(cap("record-payment")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    private AgentDescriptor homeDescriptor() {
        return new AgentDescriptor(
                "openclaw:home-agent@1",          // agentId
                "OpenClaw Home Agent",            // name
                "1",                              // version
                "openclaw",                       // provider
                "openclaw",                       // modelFamily
                null,                             // modelVersion
                null,                             // weightsFingerprint
                null,                             // domainVocabulary
                null,                             // slotVocabulary
                null,                             // dispositionVocabulary
                null,                             // axisVocabularies
                "casehubio/life/household",       // slot
                List.of(),                        // capabilities
                null,                             // disposition
                "GB",                             // jurisdiction
                null,                             // dataHandlingPolicy
                tenancyId,                        // tenancyId
                "Household maintenance agent"     // briefing
        );
    }
}
