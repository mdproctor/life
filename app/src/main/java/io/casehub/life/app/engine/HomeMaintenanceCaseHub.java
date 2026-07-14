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
import io.casehub.life.app.engine.agent.GetQuotesResult;
import io.casehub.life.app.engine.agent.IssueCommitmentResult;
import io.casehub.life.app.engine.agent.MonitorJobResult;
import io.casehub.life.app.engine.agent.RecordCompletionResult;
import io.casehub.life.app.engine.agent.ScheduleInspectionResult;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Home maintenance cycle case hub — loads the YAML definition and augments it
 * with in-process worker functions.
 *
 * <p>The two humanTask bindings (approve-contractor, verify-completion) are defined
 * in YAML and handled by {@link io.casehub.workadapter.HumanTaskScheduleHandler} —
 * no Java worker needed.
 *
 * <p>Qhorus bridge pattern: the issue-commitment worker is a STUB — in production it would
 * create a qhorus COMMAND on a case-specific channel. The monitor-job binding fires when
 * {@code QhorusMessageSignalBridge} sets {@code .channelMessage} with
 * {@code messageType == "RESPONSE"}. Refs casehub-life#6.
 */
@ApplicationScoped
public class HomeMaintenanceCaseHub extends LifeTypedCaseHub {

    public HomeMaintenanceCaseHub() {
        super("life/home-maintenance.yaml", LifeAgent.HOME);
    }

    @Override
    public LifeCaseType lifeCaseType() {
        return LifeCaseType.HOME_MAINTENANCE;
    }

    @Override
    protected void configureCase(CaseDefinition definition) {
        definition.getWorkers().add(agentWorker("schedule-inspection", """
                You are a home maintenance agent. Schedule a property inspection,
                assess the condition, and report findings.
                If cbrCalibration is provided, use featureStats for historical
                maintenance duration and severity patterns.""", ScheduleInspectionResult.class));
        definition.getWorkers().add(agentWorker("get-quotes", """
                You are a home maintenance agent. Gather contractor quotes for the
                required maintenance work.
                If cbrCalibration is provided, use featureStats.estimatedCost for
                historical cost ranges to assess quote reasonableness.""", GetQuotesResult.class));
        definition.getWorkers().add(agentWorker("issue-commitment", """
                You are a home maintenance agent. Issue a commitment to the selected
                contractor for the approved work.""", IssueCommitmentResult.class));
        definition.getWorkers().add(agentWorker("monitor-job", """
                You are a home maintenance agent. Monitor job progress and report
                estimated completion.""", MonitorJobResult.class));
        definition.getWorkers().add(agentWorker("record-completion", """
                You are a home maintenance agent. Record job completion to the
                tamper-evident ledger.""", RecordCompletionResult.class));
    }
}
