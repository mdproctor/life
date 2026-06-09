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
import jakarta.enterprise.context.ApplicationScoped;

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
        return yaml;
    }

    private static Capability cap(String name) {
        return Capability.builder().name(name).inputSchema(".").outputSchema(".").build();
    }

    /**
     * Collects budget data for review period. In production would also process
     * cross-case signals at {@code .contractorPayment}.
     */
    private Worker gatherDataWorker() {
        return Worker.builder()
                .name("gather-data-agent")
                .capabilities(List.of(cap("gather-data")))
                .function((Map<String, Object> input) -> WorkerResult.of(Map.of(
                        "totalSpend", 5000,
                        "budgetLimit", 4500,
                        "categories", List.of("groceries", "utilities", "contractor")
                )))
                .build();
    }

    /**
     * Analyses budget data for anomalies. Sets {@code hasAnomalies} flag and
     * {@code anomalyDetails} string.
     */
    private Worker analyseAnomaliesWorker() {
        return Worker.builder()
                .name("analyse-anomalies-agent")
                .capabilities(List.of(cap("analyse-anomalies")))
                .function((Map<String, Object> input) -> WorkerResult.of(Map.of(
                        "hasAnomalies", true,
                        "anomalyDetails", "Spending exceeded budget by $500 (11%)"
                )))
                .build();
    }

    /**
     * Sends COMMAND to oversight channel when anomalies detected. Adaptive — only
     * fires when {@code .analysis.hasAnomalies == true}.
     *
     * <p>In production would call {@code MessageService.dispatch()} on
     * {@code case-{caseId}/oversight}.
     */
    private Worker escalateAnomaliesWorker() {
        return Worker.builder()
                .name("escalate-anomalies-agent")
                .capabilities(List.of(cap("escalate-anomalies")))
                .function((Map<String, Object> input) -> WorkerResult.of(Map.of(
                        "escalationSent", true,
                        "channel", "case-stub/oversight"
                )))
                .build();
    }

    /**
     * Processes oversight RESPONSE decision. Fires when
     * {@code QhorusMessageSignalBridge} sets {@code .channelMessage} with
     * {@code messageType == "RESPONSE"}.
     */
    private Worker oversightResponseWorker() {
        return Worker.builder()
                .name("oversight-response-agent")
                .capabilities(List.of(cap("oversight-response")))
                .function((Map<String, Object> input) -> WorkerResult.of(Map.of(
                        "approved", true,
                        "comments", "Approved — one-time overrun acceptable"
                )))
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
     */
    private Worker produceReportWorker() {
        return Worker.builder()
                .name("produce-report-agent")
                .capabilities(List.of(cap("produce-report")))
                .function((Map<String, Object> input) -> WorkerResult.of(Map.of(
                        "reportGenerated", true,
                        "summary", "Monthly financial review complete",
                        "ledgerEntryId", "LEDGER-" + System.currentTimeMillis()
                )))
                .build();
    }
}
