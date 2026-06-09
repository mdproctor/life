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
        return yaml;
    }

    private static Capability cap(String name) {
        return Capability.builder().name(name).inputSchema(".").outputSchema(".").build();
    }

    /**
     * Issues a qhorus COMMAND on case-{caseId}/contractor-quote requesting a quote.
     * Stub returns quoteRequested flag with channel info.
     */
    private Worker requestQuoteWorker() {
        return Worker.builder()
                .name("request-quote-agent")
                .capabilities(List.of(cap("request-quote")))
                .function((Map<String, Object> input) -> WorkerResult.of(Map.of(
                        "quoteRequested", true,
                        "channel", "case-stub/contractor-quote",
                        "deadlinePassed", false
                )))
                .build();
    }

    /**
     * Sends escalation reminder when quote deadline has passed. Adaptive — only fires
     * when {@code .quoteRequest.deadlinePassed == true}.
     */
    private Worker watchdogEscalationWorker() {
        return Worker.builder()
                .name("watchdog-escalation-agent")
                .capabilities(List.of(cap("watchdog-escalation")))
                .function((Map<String, Object> input) -> WorkerResult.of(Map.of(
                        "escalated", true,
                        "reminderSent", true
                )))
                .build();
    }

    /**
     * Processes the received quote from contractor RESPONSE via QhorusMessageSignalBridge.
     */
    private Worker quoteReceivedWorker() {
        return Worker.builder()
                .name("quote-received-agent")
                .capabilities(List.of(cap("quote-received")))
                .function((Map<String, Object> input) -> WorkerResult.of(Map.of(
                        "quoteAmount", 2500,
                        "contractor", "ABC Services",
                        "validUntil", "2026-07-15"
                )))
                .build();
    }

    /**
     * Monitors job progress after quote approved.
     */
    private Worker jobMonitoringWorker() {
        return Worker.builder()
                .name("job-monitoring-agent")
                .capabilities(List.of(cap("job-monitoring")))
                .function((Map<String, Object> input) -> WorkerResult.of(Map.of(
                        "progress", "in-progress",
                        "estimatedCompletion", "2026-07-01"
                )))
                .build();
    }

    /**
     * Records payment to tamper-evident ledger and signals active financial-review case.
     *
     * <p>Stub captures the INTENT — the return map includes the data that would be
     * signaled to financial-review. In production this would:
     * <ol>
     *   <li>Call {@code FinanceDomainLedgerHandler.writeEntry(SLA_BREACH/COMPLETED, record)}</li>
     *   <li>Query {@code LifeCaseTracker.findActiveByCaseType("financial-review")}</li>
     *   <li>For each active tracker, call {@code CaseHubRuntime.signal(tracker.engineCaseId, "contractorPayment", paymentData)}</li>
     * </ol>
     */
    private Worker recordPaymentWorker() {
        return Worker.builder()
                .name("record-payment-agent")
                .capabilities(List.of(cap("record-payment")))
                .function((Map<String, Object> input) -> WorkerResult.of(Map.of(
                        "paymentRecorded", true,
                        "amount", 1500,
                        "ledgerEntryId", "LEDGER-" + System.currentTimeMillis(),
                        "crossCaseSignal", "financial-review"
                )))
                .build();
    }
}
