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
import io.casehub.api.model.OnThresholdReached;
import io.casehub.api.model.SubCase;
import io.casehub.life.api.LifeCaseType;
import io.casehub.life.app.engine.agent.BudgetAssessmentResult;
import io.casehub.life.app.engine.agent.ConfirmationResult;
import io.casehub.life.app.engine.agent.DestinationResearchResult;
import io.casehub.life.app.engine.agent.FlightSearchResult;
import io.casehub.life.app.engine.agent.HotelSearchResult;
import io.casehub.life.app.engine.agent.RebookingResult;
import io.casehub.life.app.engine.agent.TravelBookingResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Travel plan case hub — loads the YAML definition and augments it with
 * in-process worker functions and M-of-N SubCase bindings.
 *
 * <p>The humanTask binding (approval-gate) is defined in YAML and handled by
 * {@link io.casehub.workadapter.HumanTaskScheduleHandler} — no Java worker needed.
 *
 * <p>M-of-N SubCase bindings (family-vote-a/b/c) are added in Java augmentation
 * because the YAML SubCase schema does not support {@code groupId},
 * {@code totalInGroup}, {@code requiredCount}, or {@code onThresholdReached} fields.
 *
 * <p>DECLINE pattern: the booking worker returns {@code {declined: true}} when the
 * context has {@code simulateDecline == true}. The rebooking binding fires on decline
 * and returns an alternative booking. Refs casehub-life#6.
 */
@ApplicationScoped
public class TravelPlanCaseHub extends LifeTypedCaseHub {

    public TravelPlanCaseHub() {
        super("life/travel-plan.yaml", LifeAgent.TRAVEL);
    }

    @Override
    public LifeCaseType lifeCaseType() {
        return LifeCaseType.TRAVEL_PLAN;
    }

    @Override
    protected void configureCase(CaseDefinition definition) {
        definition.getWorkers().add(agentWorker("destination-research", """
                You are a travel planning agent. Research destination options with
                costs and ratings.""", DestinationResearchResult.class));
        definition.getWorkers().add(agentWorker("flight-search", """
                You are a travel planning agent. Search for flights with airline,
                price, and number of stops.""", FlightSearchResult.class));
        definition.getWorkers().add(agentWorker("hotel-search", """
                You are a travel planning agent. Search for hotels with name,
                price, and rating.""", HotelSearchResult.class));
        definition.getWorkers().add(agentWorker("budget-assessment", """
                You are a travel planning agent. Assess the total travel budget
                and determine if approval is required.""", BudgetAssessmentResult.class));
        definition.getWorkers().add(agentWorker("booking", """
                You are a travel planning agent. Book the selected flights and hotels.
                If booking fails, set declined=true with a reason.""", TravelBookingResult.class));
        definition.getWorkers().add(agentWorker("rebooking", """
                You are a travel planning agent. Rebook after a declined booking,
                finding alternative dates.""", RebookingResult.class));
        definition.getWorkers().add(agentWorker("confirmation", """
                You are a travel planning agent. Confirm the travel itinerary and
                send confirmation details.""", ConfirmationResult.class));

        // Add M-of-N SubCase bindings — YAML schema does not support these fields
        definition.getBindings().addAll(List.of(
                familyVoteBinding("family-vote-a"),
                familyVoteBinding("family-vote-b"),
                familyVoteBinding("family-vote-c")
        ));
    }

    /**
     * Creates a family-vote SubCase binding with M-of-N quorum: 2-of-3, KEEP on threshold.
     * All three bindings share the same condition and groupId — the engine coordinates
     * quorum across the group.
     */
    private Binding familyVoteBinding(String name) {
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
}
