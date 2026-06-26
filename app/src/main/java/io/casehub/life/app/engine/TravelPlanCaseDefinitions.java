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
import io.casehub.api.model.OnThresholdReached;
import io.casehub.api.model.SubCase;
import io.casehub.worker.api.Capability;

import java.time.Duration;
import java.util.Set;

/**
 * Fluent Java DSL companion for the travel-plan case definition.
 *
 * <p>Produces the same CaseDefinition as the YAML at
 * {@code life/travel-plan.yaml} plus the Java-augmented M-of-N SubCase
 * bindings, but via the Java builder API. JQ string expressions match
 * the YAML — no lambdas.
 *
 * <p>Demonstrates parallel bindings (flight-search and hotel-search fire on the
 * same condition), adaptive gates (approval-gate and family-vote fire based on
 * budget thresholds), M-of-N SubCase quorum (2-of-3 family votes), and DECLINE
 * recovery (rebooking after booking decline).
 *
 * <p>Useful for programmatic construction in tests or when the YAML parser is
 * not on the classpath.
 */
public final class TravelPlanCaseDefinitions {

    private TravelPlanCaseDefinitions() {}

    public static CaseDefinition travelPlan() {
        Capability destinationResearch = cap("destination-research",
                "Research destination options based on travel request",
                "{ request: .request }",
                "{ destinations: . }");

        Capability flightSearch = cap("flight-search",
                "Search available flights for selected destination",
                "{ selectedDestination: .selectedDestination }",
                "{ flightResults: . }");

        Capability hotelSearch = cap("hotel-search",
                "Search available hotels for selected destination",
                "{ selectedDestination: .selectedDestination }",
                "{ hotelResults: . }");

        Capability budgetAssessment = cap("budget-assessment",
                "Assess total cost and determine approval requirements",
                "{ flightResults: .flightResults, hotelResults: .hotelResults }",
                "{ budgetAssessment: . }");

        Capability booking = cap("booking",
                "Book selected flights and hotels",
                "{ selectedDestination: .selectedDestination, flightResults: .flightResults, hotelResults: .hotelResults }",
                "{ booking: . }");

        Capability rebooking = cap("rebooking",
                "Rebook with alternative dates after a booking DECLINE",
                "{ selectedDestination: .selectedDestination, booking: .booking }",
                "{ rebooking: . }");

        Capability confirmation = cap("confirmation",
                "Confirm booking and send itinerary",
                "{ booking: .booking, rebooking: .rebooking }",
                "{ confirmation: . }");

        Goal tripBooked = Goal.builder()
                .name("trip-booked")
                .kind(GoalKind.SUCCESS)
                .condition(".confirmation != null")
                .build();

        return CaseDefinition.builder()
                .namespace("casehub-life")
                .name("travel-plan")
                .version("1.0.0")
                .title("Travel plan — research, search, assess, vote/approve, book, confirm")
                .capabilities(destinationResearch, flightSearch, hotelSearch,
                        budgetAssessment, booking, rebooking, confirmation)
                .goals(tripBooked)
                .completion(GoalExpression.allOf(tripBooked))
                .bindings(
                        Binding.builder()
                                .name("destination-research")
                                .on(new ContextChangeTrigger("."))
                                .when(".request != null and .destinations == null")
                                .capability(destinationResearch)
                                .build(),
                        Binding.builder()
                                .name("flight-search")
                                .on(new ContextChangeTrigger("."))
                                .when(".selectedDestination != null and .flightResults == null")
                                .capability(flightSearch)
                                .build(),
                        Binding.builder()
                                .name("hotel-search")
                                .on(new ContextChangeTrigger("."))
                                .when(".selectedDestination != null and .hotelResults == null")
                                .capability(hotelSearch)
                                .build(),
                        Binding.builder()
                                .name("budget-assessment")
                                .on(new ContextChangeTrigger("."))
                                .when(".flightResults != null and .hotelResults != null and .budgetAssessment == null")
                                .capability(budgetAssessment)
                                .build(),
                        // M-of-N SubCase: 3 family-vote bindings, 2-of-3 quorum, KEEP on threshold
                        familyVoteBinding("family-vote-a"),
                        familyVoteBinding("family-vote-b"),
                        familyVoteBinding("family-vote-c"),
                        Binding.builder()
                                .name("approval-gate")
                                .on(new ContextChangeTrigger("."))
                                .when(".budgetAssessment != null and .budgetAssessment.requiresApproval == true and .budgetAssessment.isHighValue == false and .approval == null")
                                .humanTask(HumanTaskTarget.inline()
                                        .title("Approve travel booking")
                                        .expiresIn(Duration.ofHours(24))
                                        .candidateGroups(Set.of("household-admin"))
                                        .scope("casehubio/life/finance")
                                        .inputMapping("{ budgetAssessment: .budgetAssessment, selectedDestination: .selectedDestination }")
                                        .outputMapping("{ approval: . }")
                                        .build())
                                .build(),
                        Binding.builder()
                                .name("booking")
                                .on(new ContextChangeTrigger("."))
                                .when(".budgetAssessment != null and .booking == null and ((.budgetAssessment.requiresApproval != true) or (.approval != null) or (.familyVoteResult != null))")
                                .capability(booking)
                                .build(),
                        Binding.builder()
                                .name("rebooking")
                                .on(new ContextChangeTrigger("."))
                                .when(".booking != null and .booking.declined == true and .rebooking == null")
                                .capability(rebooking)
                                .build(),
                        Binding.builder()
                                .name("confirmation")
                                .on(new ContextChangeTrigger("."))
                                .when("(.booking != null and .booking.declined != true and .confirmation == null) or (.rebooking != null and .confirmation == null)")
                                .capability(confirmation)
                                .build()
                )
                .build();
    }

    /**
     * Creates a family-vote SubCase binding with M-of-N quorum: 2-of-3, KEEP on threshold.
     */
    private static Binding familyVoteBinding(String name) {
        return Binding.builder()
                .name(name)
                .on(new ContextChangeTrigger("."))
                .when(".budgetAssessment != null and .budgetAssessment.isHighValue == true and .familyVoteResult == null")
                .subCase(SubCase.builder()
                        .namespace("life")
                        .name("family-vote")
                        .version("1.0.0")
                        .groupId("family-vote")
                        .totalInGroup(3)
                        .requiredCount(2)
                        .onThresholdReached(OnThresholdReached.KEEP)
                        .inputMapping("{ proposal: .selectedDestination, estimatedCost: .budgetAssessment.totalCost }")
                        .outputMapping("{ familyVoteResult: . }")
                        .build())
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
