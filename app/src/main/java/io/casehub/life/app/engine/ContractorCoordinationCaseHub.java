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
import io.casehub.life.api.LifeCaseType;
import io.casehub.life.app.engine.agent.JobMonitoringResult;
import io.casehub.life.app.engine.agent.QuoteReceivedResult;
import io.casehub.life.app.engine.agent.RecordPaymentResult;
import io.casehub.life.app.engine.agent.RequestQuoteResult;
import io.casehub.life.app.engine.agent.WatchdogEscalationResult;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Contractor coordination case hub — loads the YAML definition and augments it
 * with in-process worker functions.
 *
 * <p>The two humanTask bindings (approve-quote, payment-gate) are defined in YAML
 * and handled by {@link io.casehub.workadapter.HumanTaskScheduleHandler} — no Java
 * worker needed.
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
public class ContractorCoordinationCaseHub extends LifeTypedCaseHub {

    public ContractorCoordinationCaseHub() {
        super("life/contractor-coordination.yaml", LifeAgent.HOME);
    }

    @Override
    public LifeCaseType lifeCaseType() {
        return LifeCaseType.CONTRACTOR_COORDINATION;
    }

    @Override
    protected void configureCase(CaseDefinition definition) {
        definition.getWorkers().add(agentWorker("request-quote", """
                You are a contractor coordination agent. Request a quote from the
                contractor via the appropriate messaging channel.""", RequestQuoteResult.class));
        definition.getWorkers().add(agentWorker("watchdog-escalation", """
                You are a contractor coordination agent. Escalate an overdue
                contractor commitment by sending a reminder.""", WatchdogEscalationResult.class));
        definition.getWorkers().add(agentWorker("quote-received", """
                You are a contractor coordination agent. Process a received quote,
                extracting amount, contractor details, and validity period.""", QuoteReceivedResult.class));
        definition.getWorkers().add(agentWorker("job-monitoring", """
                You are a contractor coordination agent. Monitor an active contractor
                job and report progress.""", JobMonitoringResult.class));
        definition.getWorkers().add(agentWorker("record-payment", """
                You are a contractor coordination agent. Record a contractor payment
                to the tamper-evident ledger and emit a cross-case signal.""", RecordPaymentResult.class));
    }
}
