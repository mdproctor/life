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
import io.casehub.worker.api.Capability;

/**
 * Fluent Java DSL companion for the financial-review case definition.
 *
 * <p>Produces the same CaseDefinition as the YAML at
 * {@code life/financial-review.yaml} but via the Java builder API. JQ string
 * expressions match the YAML — no lambdas.
 *
 * <p>Demonstrates cross-case signal reception (contractor payment signals),
 * adaptive escalation (fires only when {@code .analysis.hasAnomalies == true}),
 * and qhorus oversight gate (COMMAND to oversight channel, awaits RESPONSE).
 *
 * <p>Useful for programmatic construction in tests or when the YAML parser is
 * not on the classpath.
 */
public final class FinancialReviewCaseDefinitions {

    private FinancialReviewCaseDefinitions() {}

    public static CaseDefinition financialReview() {
        Capability gatherData = cap("gather-data",
                "Collect budget data for review period and process cross-case contractor payment signals",
                "{ reviewPeriod: .reviewPeriod, contractorPayment: .contractorPayment }",
                "{ budgetData: . }");

        Capability analyseAnomalies = cap("analyse-anomalies",
                "Analyse budget data for anomalies and flag issues",
                "{ budgetData: .budgetData }",
                "{ analysis: . }");

        Capability escalateAnomalies = cap("escalate-anomalies",
                "Send COMMAND to oversight channel for anomaly review",
                "{ analysis: .analysis }",
                "{ escalation: . }");

        Capability oversightResponse = cap("oversight-response",
                "Process oversight RESPONSE decision via QhorusMessageSignalBridge",
                "{ channelMessage: .channelMessage }",
                "{ oversightDecision: . }");

        Capability produceReport = cap("produce-report",
                "Generate financial review report and record to ledger",
                "{ analysis: .analysis, oversightDecision: .oversightDecision }",
                "{ report: . }");

        Goal reviewComplete = Goal.builder()
                .name("review-complete")
                .kind(GoalKind.SUCCESS)
                .condition(".report != null")
                .build();

        return CaseDefinition.builder()
                .namespace("casehub-life")
                .name("financial-review")
                .version("1.0.0")
                .title("Financial review — gather data, analyse, escalate anomalies, oversight gate, report")
                .capabilities(gatherData, analyseAnomalies, escalateAnomalies,
                        oversightResponse, produceReport)
                .goals(reviewComplete)
                .completion(GoalExpression.allOf(reviewComplete))
                .bindings(
                        Binding.builder()
                                .name("gather-data")
                                .on(new ContextChangeTrigger("."))
                                .when(".reviewPeriod != null and .budgetData == null")
                                .capability(gatherData)
                                .build(),
                        Binding.builder()
                                .name("analyse-anomalies")
                                .on(new ContextChangeTrigger("."))
                                .when(".budgetData != null and .analysis == null")
                                .capability(analyseAnomalies)
                                .build(),
                        Binding.builder()
                                .name("escalate-anomalies")
                                .on(new ContextChangeTrigger("."))
                                .when(".analysis != null and .analysis.hasAnomalies == true and .escalation == null")
                                .capability(escalateAnomalies)
                                .build(),
                        Binding.builder()
                                .name("oversight-response")
                                .on(new ContextChangeTrigger("."))
                                .when(".channelMessage != null and .channelMessage.messageType == \"RESPONSE\" and .oversightDecision == null")
                                .capability(oversightResponse)
                                .build(),
                        Binding.builder()
                                .name("produce-report")
                                .on(new ContextChangeTrigger("."))
                                .when("(.analysis != null and .analysis.hasAnomalies != true and .report == null) or (.oversightDecision != null and .report == null)")
                                .capability(produceReport)
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
