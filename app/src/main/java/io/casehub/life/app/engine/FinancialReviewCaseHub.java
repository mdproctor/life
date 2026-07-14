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
import io.casehub.life.app.engine.agent.AnalyseAnomaliesResult;
import io.casehub.life.app.engine.agent.EscalateAnomaliesResult;
import io.casehub.life.app.engine.agent.GatherDataResult;
import io.casehub.life.app.engine.agent.OversightResponseResult;
import io.casehub.life.app.engine.agent.ProduceReportResult;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Financial review case hub — loads the YAML definition and augments it with
 * in-process worker functions.
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
public class FinancialReviewCaseHub extends LifeTypedCaseHub {

    public FinancialReviewCaseHub() {
        super("life/financial-review.yaml", LifeAgent.FINANCE);
    }

    @Override
    public LifeCaseType lifeCaseType() {
        return LifeCaseType.FINANCIAL_REVIEW;
    }

    @Override
    protected void configureCase(CaseDefinition definition) {
        definition.getWorkers().add(agentWorker("gather-data", """
                You are a financial review agent. Gather financial data by aggregating
                transactions across all linked accounts.""", GatherDataResult.class));
        definition.getWorkers().add(agentWorker("analyse-anomalies", """
                You are a financial review agent. Analyse spending anomalies by
                comparing current spending patterns against budget limits.
                If cbrCalibration is provided, use featureStats.estimatedBudget for
                historical spending patterns and threshold calibration.""", AnalyseAnomaliesResult.class));
        definition.getWorkers().add(agentWorker("escalate-anomalies", """
                You are a financial review agent. Escalate anomalies to the oversight
                channel for human review.""", EscalateAnomaliesResult.class));
        definition.getWorkers().add(agentWorker("oversight-response", """
                You are a financial review agent. Process oversight response from
                the household admin regarding flagged anomalies.""", OversightResponseResult.class));
        definition.getWorkers().add(agentWorker("produce-report", """
                You are a financial review agent. Produce a monthly financial report
                summarising spending and recording it to the ledger.""", ProduceReportResult.class));
    }
}
