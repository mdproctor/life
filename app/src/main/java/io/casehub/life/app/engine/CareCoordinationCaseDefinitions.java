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
import io.casehub.api.model.Milestone;
import io.casehub.api.model.SubCase;
import io.casehub.worker.api.Capability;

import java.time.Duration;
import java.util.Set;

/**
 * Fluent Java DSL companion for the care-coordination case definition.
 *
 * <p>Produces the same CaseDefinition as the YAML at
 * {@code life/care-coordination.yaml} but via the Java builder API. JQ string
 * expressions match the YAML — no lambdas.
 *
 * <p>Demonstrates SubCase lifecycle (care-episode child case), milestones
 * (assessment-complete, carer-assigned), and adaptive escalation
 * (escalate-concern fires only when healthConcern is true).
 *
 * <p>Useful for programmatic construction in tests or when the YAML parser is
 * not on the classpath.
 */
public final class CareCoordinationCaseDefinitions {

    private CareCoordinationCaseDefinitions() {}

    public static CaseDefinition careCoordination() {
        Capability needsAssessment = cap("needs-assessment",
                "Assess care needs based on the care request",
                "{ careRequest: .careRequest }",
                "{ assessment: . }");

        Capability carePlan = cap("care-plan",
                "Produce a care schedule based on the assessment",
                "{ assessment: .assessment }",
                "{ carePlan: . }");

        Capability healthCheck = cap("health-check",
                "Analyse care notes from episode and flag health concerns",
                "{ episodeResult: .episodeResult, carePlan: .carePlan }",
                "{ healthCheck: . }");

        Goal reviewComplete = Goal.builder()
                .name("review-complete")
                .kind(GoalKind.SUCCESS)
                .condition(".careReview != null")
                .build();

        return CaseDefinition.builder()
                .namespace("casehub-life")
                .name("care-coordination")
                .version("1.0.0")
                .title("Care coordination — assess, plan, assign, episode, health check, escalate, review")
                .capabilities(needsAssessment, carePlan, healthCheck)
                .milestones(
                        Milestone.builder()
                                .name("assessment-complete")
                                .completionCriteria(".assessment != null")
                                .build(),
                        Milestone.builder()
                                .name("carer-assigned")
                                .completionCriteria(".carerAssignment != null")
                                .build()
                )
                .goals(reviewComplete)
                .completion(GoalExpression.allOf(reviewComplete))
                .bindings(
                        Binding.builder()
                                .name("needs-assessment")
                                .on(new ContextChangeTrigger("."))
                                .when(".careRequest != null and .assessment == null")
                                .capability(needsAssessment)
                                .build(),
                        Binding.builder()
                                .name("care-plan")
                                .on(new ContextChangeTrigger("."))
                                .when(".assessment != null and .carePlan == null")
                                .capability(carePlan)
                                .build(),
                        Binding.builder()
                                .name("assign-carer")
                                .on(new ContextChangeTrigger("."))
                                .when(".carePlan != null and .carerAssignment == null")
                                .humanTask(HumanTaskTarget.inline()
                                        .title("Accept care delegation")
                                        .expiresIn(Duration.ofHours(24))
                                        .candidateGroups(Set.of("household-member"))
                                        .scope("casehubio/life/elder-care")
                                        .inputMapping("{ careRequest: .careRequest, carePlan: .carePlan }")
                                        .outputMapping("{ carerAssignment: . }")
                                        .build())
                                .build(),
                        Binding.builder()
                                .name("care-episode")
                                .on(new ContextChangeTrigger("."))
                                .when(".carerAssignment != null and .episodeResult == null")
                                .subCase(SubCase.builder()
                                        .namespace("casehub-life")
                                        .name("care-episode")
                                        .version("1.0.0")
                                        .waitForCompletion(true)
                                        .inputMapping("{ careRequest: .careRequest, carePlan: .carePlan }")
                                        .outputMapping("{ episodeResult: . }")
                                        .build())
                                .build(),
                        Binding.builder()
                                .name("health-check")
                                .on(new ContextChangeTrigger("."))
                                .when(".episodeResult != null and .healthCheck == null")
                                .capability(healthCheck)
                                .build(),
                        Binding.builder()
                                .name("escalate-concern")
                                .on(new ContextChangeTrigger("."))
                                .when(".healthCheck != null and .healthCheck.healthConcern == true and .escalation == null")
                                .humanTask(HumanTaskTarget.inline()
                                        .title("Review health concern escalation")
                                        .expiresIn(Duration.ofHours(12))
                                        .candidateGroups(Set.of("household-admin"))
                                        .scope("casehubio/life/elder-care")
                                        .inputMapping("{ healthCheck: .healthCheck, episodeResult: .episodeResult, careRequest: .careRequest }")
                                        .outputMapping("{ escalation: . }")
                                        .build())
                                .build(),
                        Binding.builder()
                                .name("care-review")
                                .on(new ContextChangeTrigger("."))
                                .when("(.healthCheck != null and .healthCheck.healthConcern != true and .careReview == null) or (.escalation != null and .careReview == null)")
                                .humanTask(HumanTaskTarget.inline()
                                        .title("Review care quality")
                                        .expiresIn(Duration.ofHours(48))
                                        .candidateGroups(Set.of("household-admin"))
                                        .scope("casehubio/life/elder-care")
                                        .inputMapping("{ healthCheck: .healthCheck, episodeResult: .episodeResult, escalation: .escalation }")
                                        .outputMapping("{ careReview: . }")
                                        .build())
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
