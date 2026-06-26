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
 * Fluent Java DSL companion for the contractor-coordination case definition.
 *
 * <p>Produces the same CaseDefinition as the YAML at
 * {@code life/contractor-coordination.yaml} but via the Java builder API. JQ string
 * expressions match the YAML — no lambdas.
 *
 * <p>Demonstrates the full qhorus lifecycle: COMMAND (request-quote), Watchdog
 * (watchdog-escalation, adaptive), RESPONSE (quote-received via channelMessage),
 * and cross-case signal intent (record-payment).
 *
 * <p>Useful for programmatic construction in tests or when the YAML parser is
 * not on the classpath.
 */
public final class ContractorCoordinationCaseDefinitions {

    private ContractorCoordinationCaseDefinitions() {}

    public static CaseDefinition contractorCoordination() {
        Capability requestQuote = cap("request-quote",
                "Issue a qhorus COMMAND on case-{caseId}/contractor-quote requesting a quote",
                "{ contractorRequest: .contractorRequest }",
                "{ quoteRequest: . }");

        Capability watchdogEscalation = cap("watchdog-escalation",
                "Send escalation reminder when quote deadline has passed",
                "{ quoteRequest: .quoteRequest }",
                "{ escalationSent: . }");

        Capability quoteReceived = cap("quote-received",
                "Process the received quote from contractor RESPONSE via QhorusMessageSignalBridge",
                "{ channelMessage: .channelMessage }",
                "{ quoteResponse: . }");

        Capability jobMonitoring = cap("job-monitoring",
                "Monitor job progress after quote approved",
                "{ quoteApproval: .quoteApproval }",
                "{ jobStatus: . }");

        Capability recordPayment = cap("record-payment",
                "Record payment to tamper-evident ledger and signal active financial-review case",
                "{ paymentConfirmation: .paymentConfirmation, quoteResponse: .quoteResponse }",
                "{ paymentRecorded: . }");

        Goal contractorPaid = Goal.builder()
                .name("contractor-paid")
                .kind(GoalKind.SUCCESS)
                .condition(".paymentRecorded != null")
                .build();

        return CaseDefinition.builder()
                .namespace("casehub-life")
                .name("contractor-coordination")
                .version("1.0.0")
                .title("Contractor coordination — quote, watchdog, respond, approve, monitor, pay, record")
                .capabilities(requestQuote, watchdogEscalation, quoteReceived,
                        jobMonitoring, recordPayment)
                .goals(contractorPaid)
                .completion(GoalExpression.allOf(contractorPaid))
                .bindings(
                        Binding.builder()
                                .name("request-quote")
                                .on(new ContextChangeTrigger("."))
                                .when(".contractorRequest != null and .quoteRequest == null")
                                .capability(requestQuote)
                                .build(),
                        Binding.builder()
                                .name("watchdog-escalation")
                                .on(new ContextChangeTrigger("."))
                                .when(".quoteRequest != null and .quoteRequest.deadlinePassed == true and .escalationSent == null")
                                .capability(watchdogEscalation)
                                .build(),
                        Binding.builder()
                                .name("quote-received")
                                .on(new ContextChangeTrigger("."))
                                .when(".channelMessage != null and .channelMessage.messageType == \"RESPONSE\" and .quoteResponse == null")
                                .capability(quoteReceived)
                                .build(),
                        Binding.builder()
                                .name("approve-quote")
                                .on(new ContextChangeTrigger("."))
                                .when(".quoteResponse != null and .quoteApproval == null")
                                .humanTask(HumanTaskTarget.inline()
                                        .title("Approve contractor quote")
                                        .expiresIn(Duration.ofHours(72))
                                        .candidateGroups(Set.of("household-admin"))
                                        .scope("casehubio/life/household")
                                        .inputMapping("{ quoteResponse: .quoteResponse, contractorRequest: .contractorRequest }")
                                        .outputMapping("{ quoteApproval: . }")
                                        .build())
                                .build(),
                        Binding.builder()
                                .name("job-monitoring")
                                .on(new ContextChangeTrigger("."))
                                .when(".quoteApproval != null and .jobStatus == null")
                                .capability(jobMonitoring)
                                .build(),
                        Binding.builder()
                                .name("payment-gate")
                                .on(new ContextChangeTrigger("."))
                                .when(".jobStatus != null and .paymentConfirmation == null")
                                .humanTask(HumanTaskTarget.inline()
                                        .title("Confirm contractor payment")
                                        .expiresIn(Duration.ofHours(168))
                                        .candidateGroups(Set.of("household-admin"))
                                        .scope("casehubio/life/finance")
                                        .inputMapping("{ jobStatus: .jobStatus, quoteApproval: .quoteApproval, quoteResponse: .quoteResponse }")
                                        .outputMapping("{ paymentConfirmation: . }")
                                        .build())
                                .build(),
                        Binding.builder()
                                .name("record-payment")
                                .on(new ContextChangeTrigger("."))
                                .when(".paymentConfirmation != null and .paymentRecorded == null")
                                .capability(recordPayment)
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
