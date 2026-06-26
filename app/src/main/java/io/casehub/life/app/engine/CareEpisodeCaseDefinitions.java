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
 * Fluent Java DSL companion for the care-episode case definition.
 *
 * <p>Produces the same CaseDefinition as the YAML at
 * {@code life/care-episode.yaml} but via the Java builder API. JQ string
 * expressions match the YAML — no lambdas.
 *
 * <p>This is a child case spawned by care-coordination via SubCase binding.
 * Not used directly by LifeCaseService.
 *
 * <p>Useful for programmatic construction in tests or when the YAML parser is
 * not on the classpath.
 */
public final class CareEpisodeCaseDefinitions {

    private CareEpisodeCaseDefinitions() {}

    public static CaseDefinition careEpisode() {
        Capability assessPatient = cap("assess-patient",
                "Assess patient condition at start of care episode",
                "{ careRequest: .careRequest }",
                "{ patientAssessment: . }");

        Capability provideCare = cap("provide-care",
                "Provide care based on patient assessment",
                "{ patientAssessment: .patientAssessment, carePlan: .carePlan }",
                "{ careProvided: . }");

        Goal episodeComplete = Goal.builder()
                .name("episode-complete")
                .kind(GoalKind.SUCCESS)
                .condition(".careNotes != null")
                .build();

        return CaseDefinition.builder()
                .namespace("casehub-life")
                .name("care-episode")
                .version("1.0.0")
                .title("Care episode — assess patient, provide care, record notes")
                .capabilities(assessPatient, provideCare)
                .goals(episodeComplete)
                .completion(GoalExpression.allOf(episodeComplete))
                .bindings(
                        Binding.builder()
                                .name("assess-patient")
                                .on(new ContextChangeTrigger("."))
                                .when(".careRequest != null and .patientAssessment == null")
                                .capability(assessPatient)
                                .build(),
                        Binding.builder()
                                .name("provide-care")
                                .on(new ContextChangeTrigger("."))
                                .when(".patientAssessment != null and .careProvided == null")
                                .capability(provideCare)
                                .build(),
                        Binding.builder()
                                .name("record-notes")
                                .on(new ContextChangeTrigger("."))
                                .when(".careProvided != null and .careNotes == null")
                                .humanTask(HumanTaskTarget.inline()
                                        .title("Record care visit notes")
                                        .expiresIn(Duration.ofHours(24))
                                        .candidateGroups(Set.of("household-member"))
                                        .scope("casehubio/life/elder-care")
                                        .inputMapping("{ patientAssessment: .patientAssessment, careProvided: .careProvided }")
                                        .outputMapping("{ careNotes: . }")
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
