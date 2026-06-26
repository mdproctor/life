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

import io.casehub.api.model.Binding;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.api.model.Goal;
import io.casehub.api.model.GoalExpression;
import io.casehub.api.model.GoalKind;
import io.casehub.api.model.HumanTaskTarget;
import io.casehub.worker.api.Capability;

import java.time.Duration;
import java.util.Set;

/**
 * Fluent Java DSL companion for the home-maintenance case definition.
 *
 * <p>Produces the same CaseDefinition as the YAML at
 * {@code life/home-maintenance.yaml} but via the Java builder API. JQ string
 * expressions match the YAML — no lambdas.
 *
 * <p>Demonstrates the qhorus bridge pattern: the monitor-job binding condition
 * checks {@code .channelMessage.messageType == "RESPONSE"}, which in production
 * would be set by {@code QhorusMessageSignalBridge}.
 *
 * <p>Useful for programmatic construction in tests or when the YAML parser is
 * not on the classpath.
 */
public final class HomeMaintenanceCaseDefinitions {

    private HomeMaintenanceCaseDefinitions() {}

    public static CaseDefinition homeMaintenance() {
        Capability scheduleInspection = cap("schedule-inspection",
                "Schedule and perform a home inspection",
                "{ request: .request }",
                "{ inspection: . }");

        Capability getQuotes = cap("get-quotes",
                "Obtain contractor quotes based on inspection results",
                "{ inspection: .inspection }",
                "{ quotes: . }");

        Capability issueCommitment = cap("issue-commitment",
                "Issue a qhorus COMMAND to the selected contractor on a case-specific channel",
                "{ contractorApproval: .contractorApproval, inspection: .inspection }",
                "{ commitmentIssued: . }");

        Capability monitorJob = cap("monitor-job",
                "Monitor job progress after contractor RESPONSE received via QhorusMessageSignalBridge",
                "{ channelMessage: .channelMessage, commitmentIssued: .commitmentIssued }",
                "{ jobStatus: . }");

        Capability recordCompletion = cap("record-completion",
                "Record job completion to tamper-evident ledger",
                "{ completionVerification: .completionVerification, inspection: .inspection, contractorApproval: .contractorApproval }",
                "{ completionRecord: . }");

        Goal jobComplete = Goal.builder()
                .name("job-complete")
                .kind(GoalKind.SUCCESS)
                .condition(".completionRecord != null")
                .build();

        return CaseDefinition.builder()
                .namespace("casehub-life")
                .name("home-maintenance")
                .version("1.0.0")
                .title("Home maintenance cycle — inspect, quote, approve, commit, monitor, verify, record")
                .capabilities(scheduleInspection, getQuotes, issueCommitment,
                        monitorJob, recordCompletion)
                .goals(jobComplete)
                .completion(GoalExpression.allOf(jobComplete))
                .bindings(
                        Binding.builder()
                                .name("schedule-inspection")
                                .on(new ContextChangeTrigger("."))
                                .when(".request != null and .inspection == null")
                                .capability(scheduleInspection)
                                .build(),
                        Binding.builder()
                                .name("get-quotes")
                                .on(new ContextChangeTrigger("."))
                                .when(".inspection != null and .quotes == null")
                                .capability(getQuotes)
                                .build(),
                        Binding.builder()
                                .name("approve-contractor")
                                .on(new ContextChangeTrigger("."))
                                .when(".quotes != null and .contractorApproval == null")
                                .humanTask(HumanTaskTarget.inline()
                                        .title("Select a contractor quote")
                                        .expiresIn(Duration.ofHours(72))
                                        .candidateGroups(Set.of("household-admin"))
                                        .scope("casehubio/life/household")
                                        .inputMapping("{ quotes: .quotes, inspection: .inspection }")
                                        .outputMapping("{ contractorApproval: . }")
                                        .build())
                                .build(),
                        Binding.builder()
                                .name("issue-commitment")
                                .on(new ContextChangeTrigger("."))
                                .when(".contractorApproval != null and .commitmentIssued == null")
                                .capability(issueCommitment)
                                .build(),
                        Binding.builder()
                                .name("monitor-job")
                                .on(new ContextChangeTrigger("."))
                                .when(".channelMessage != null and .channelMessage.messageType == \"RESPONSE\" and .jobStatus == null")
                                .capability(monitorJob)
                                .build(),
                        Binding.builder()
                                .name("verify-completion")
                                .on(new ContextChangeTrigger("."))
                                .when(".jobStatus != null and .completionVerification == null")
                                .humanTask(HumanTaskTarget.inline()
                                        .title("Verify job completion")
                                        .expiresIn(Duration.ofHours(24))
                                        .candidateGroups(Set.of("household-member"))
                                        .scope("casehubio/life/household")
                                        .inputMapping("{ jobStatus: .jobStatus, contractorApproval: .contractorApproval }")
                                        .outputMapping("{ completionVerification: . }")
                                        .build())
                                .build(),
                        Binding.builder()
                                .name("record-completion")
                                .on(new ContextChangeTrigger("."))
                                .when(".completionVerification != null and .completionRecord == null")
                                .capability(recordCompletion)
                                .build()
                )
                .build();
    }

    private static Capability cap(String name, String description,
                                  String inputSchema, String outputSchema) {
        return Capability.builder()
                .name(name)
                .description(description)
                .inputSchema(inputSchema)
                .outputSchema(outputSchema)
                .build();
    }
}
