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
import io.casehub.life.app.engine.agent.AnalyseAnomaliesResult;
import io.casehub.life.app.engine.agent.EscalateAnomaliesResult;
import io.casehub.life.app.engine.agent.GatherDataResult;
import io.casehub.life.app.engine.agent.LifeOpenClawChatModelFactory;
import io.casehub.life.app.engine.agent.OversightResponseResult;
import io.casehub.life.app.engine.agent.ProduceReportResult;
import io.casehub.api.model.AgentWorkerFunction;
import io.casehub.worker.api.Capability;
import io.casehub.worker.api.Worker;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;

/**
 * Financial review case hub — loads the YAML definition and augments it with
 * in-process worker functions.
 *
 * <p>Workers are lambda functions that run on Quartz worker threads.
 *
 * <p>Demonstrates cross-case signal reception (contractor payment signals at
 * {@code .contractorPayment}), adaptive escalation (fires only when
 * {@code .analysis.hasAnomalies == true}), and qhorus oversight gate (COMMAND
 * to oversight channel, awaits RESPONSE via {@code QhorusMessageSignalBridge}).
 *
 * <p>Five-step workflow:
 * <ol>
 *   <li>gather-data — collects budget data and processes cross-case signals</li>
 *   <li>analyse-anomalies — detects issues, sets hasAnomalies flag</li>
 *   <li>escalate-anomalies — ADAPTIVE: sends COMMAND to oversight channel if anomalies detected</li>
 *   <li>oversight-response — processes RESPONSE from oversight channel</li>
 *   <li>produce-report — generates summary and records ledger entry</li>
 * </ol>
 *
 * <p>Refs casehub-life#6.
 */
@ApplicationScoped
public class FinancialReviewCaseHub extends YamlCaseHub {

    @Inject
    LifeOpenClawChatModelFactory openClawFactory;

    @ConfigProperty(name = "casehub.life.tenancy-id")
    String tenancyId;

    private volatile CaseDefinition augmentedDefinition;

    public FinancialReviewCaseHub() {
        super("life/financial-review.yaml");
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
                gatherDataWorker(),
                analyseAnomaliesWorker(),
                escalateAnomaliesWorker(),
                oversightResponseWorker(),
                produceReportWorker()
        ));
        yaml.setAgentDescriptors(Map.of("openclaw:finance-agent@1", financeDescriptor()));
        return yaml;
    }

    private static Capability cap(String name) {
        return Capability.builder().name(name).inputSchema(".").outputSchema(".").build();
    }

    /**
     * Collects budget data for review period. In production would also process
     * cross-case signals at {@code .contractorPayment}.
     *
     * <p>Uses OpenClaw's LLM API to gather financial data by aggregating
     * transactions across all linked accounts.
     */
    private Worker gatherDataWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("finance-agent"))
                .systemPrompt("""
                        You are a financial review agent. Gather financial data by aggregating
                        transactions across all linked accounts.""")
                .responseSchema(GatherDataResult.class)
                .build();

        return Worker.builder()
                .name("gather-data-agent")
                .capabilities(List.of(cap("gather-data")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    /**
     * Analyses budget data for anomalies. Sets {@code hasAnomalies} flag and
     * {@code anomalyDetails} string.
     *
     * <p>Uses OpenClaw's LLM API to analyse spending anomalies by
     * comparing current spending patterns against budget limits.
     */
    private Worker analyseAnomaliesWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("finance-agent"))
                .systemPrompt("""
                        You are a financial review agent. Analyse spending anomalies by
                        comparing current spending patterns against budget limits.""")
                .responseSchema(AnalyseAnomaliesResult.class)
                .build();

        return Worker.builder()
                .name("analyse-anomalies-agent")
                .capabilities(List.of(cap("analyse-anomalies")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    /**
     * Sends COMMAND to oversight channel when anomalies detected. Adaptive — only
     * fires when {@code .analysis.hasAnomalies == true}.
     *
     * <p>In production would call {@code MessageService.dispatch()} on
     * {@code case-{caseId}/oversight}.
     *
     * <p>Uses OpenClaw's LLM API to escalate anomalies to the oversight
     * channel for human review.
     */
    private Worker escalateAnomaliesWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("finance-agent"))
                .systemPrompt("""
                        You are a financial review agent. Escalate anomalies to the oversight
                        channel for human review.""")
                .responseSchema(EscalateAnomaliesResult.class)
                .build();

        return Worker.builder()
                .name("escalate-anomalies-agent")
                .capabilities(List.of(cap("escalate-anomalies")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    /**
     * Processes oversight RESPONSE decision. Fires when
     * {@code QhorusMessageSignalBridge} sets {@code .channelMessage} with
     * {@code messageType == "RESPONSE"}.
     *
     * <p>Uses OpenClaw's LLM API to process oversight response from
     * the household admin regarding flagged anomalies.
     */
    private Worker oversightResponseWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("finance-agent"))
                .systemPrompt("""
                        You are a financial review agent. Process oversight response from
                        the household admin regarding flagged anomalies.""")
                .responseSchema(OversightResponseResult.class)
                .build();

        return Worker.builder()
                .name("oversight-response-agent")
                .capabilities(List.of(cap("oversight-response")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    /**
     * Generates financial review report and records ledger entry. Fires when:
     * <ul>
     *   <li>Analysis complete AND no anomalies detected, OR</li>
     *   <li>Oversight decision received (after anomaly escalation)</li>
     * </ul>
     *
     * <p>In production would call {@code FinanceDomainLedgerHandler.writeEntry()}.
     *
     * <p>Uses OpenClaw's LLM API to produce a monthly financial report
     * summarising spending and recording it to the ledger.
     */
    private Worker produceReportWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("finance-agent"))
                .systemPrompt("""
                        You are a financial review agent. Produce a monthly financial report
                        summarising spending and recording it to the ledger.""")
                .responseSchema(ProduceReportResult.class)
                .build();

        return Worker.builder()
                .name("produce-report-agent")
                .capabilities(List.of(cap("produce-report")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    private AgentDescriptor financeDescriptor() {
        return new AgentDescriptor(
                "openclaw:finance-agent@1",      // agentId
                "OpenClaw Finance Agent",        // name
                "1",                             // version
                "openclaw",                      // provider
                "openclaw",                      // modelFamily
                null,                            // modelVersion
                null,                            // weightsFingerprint
                null,                            // domainVocabulary
                null,                            // slotVocabulary
                null,                            // dispositionVocabulary
                null,                            // axisVocabularies
                "casehubio/life/finance",        // slot
                List.of(),                       // capabilities
                null,                            // disposition
                "GB",                            // jurisdiction
                null,                            // dataHandlingPolicy
                tenancyId,                       // tenancyId
                "Financial review and governance agent" // briefing
        );
    }
}
