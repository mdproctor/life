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

import io.casehub.api.engine.YamlCaseHub;
import io.casehub.api.model.Binding;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.api.model.OnThresholdReached;
import io.casehub.api.model.SubCase;
import io.casehub.api.model.ai.Agent;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.life.app.engine.agent.BudgetAssessmentResult;
import io.casehub.life.app.engine.agent.ConfirmationResult;
import io.casehub.life.app.engine.agent.DestinationResearchResult;
import io.casehub.life.app.engine.agent.FlightSearchResult;
import io.casehub.life.app.engine.agent.HotelSearchResult;
import io.casehub.life.app.engine.agent.LifeOpenClawChatModelFactory;
import io.casehub.life.app.engine.agent.RebookingResult;
import io.casehub.life.app.engine.agent.TravelBookingResult;
import io.casehub.api.model.AgentWorkerFunction;
import io.casehub.worker.api.Capability;
import io.casehub.worker.api.Worker;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;

/**
 * Travel plan case hub — loads the YAML definition and augments it with
 * in-process worker functions and M-of-N SubCase bindings.
 *
 * <p>Workers are lambda functions that run on Quartz worker threads. The humanTask
 * binding (approval-gate) is defined in YAML and handled by
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
public class TravelPlanCaseHub extends YamlCaseHub {

    @Inject
    LifeOpenClawChatModelFactory openClawFactory;

    @ConfigProperty(name = "casehub.life.tenancy-id")
    String tenancyId;

    private volatile CaseDefinition augmentedDefinition;

    public TravelPlanCaseHub() {
        super("life/travel-plan.yaml");
    }

    @Override
    public CaseDefinition getDefinition() {
        if (augmentedDefinition == null) {
            synchronized (this) {
                if (augmentedDefinition == null) {
                    augmentedDefinition = augment(super.getDefinition());
                }
            }
        }
        return augmentedDefinition;
    }

    private CaseDefinition augment(CaseDefinition yaml) {
        // Add workers for all capability bindings
        yaml.getWorkers().addAll(List.of(
                destinationResearchWorker(),
                flightSearchWorker(),
                hotelSearchWorker(),
                budgetAssessmentWorker(),
                bookingWorker(),
                rebookingWorker(),
                confirmationWorker()
        ));

        // Add M-of-N SubCase bindings — YAML schema does not support these fields
        yaml.getBindings().addAll(List.of(
                familyVoteBinding("family-vote-a"),
                familyVoteBinding("family-vote-b"),
                familyVoteBinding("family-vote-c")
        ));

        yaml.setAgentDescriptors(Map.of("openclaw:travel-agent@1", travelDescriptor()));
        return yaml;
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

    private static Capability cap(String name) {
        return Capability.builder().name(name).inputSchema(".").outputSchema(".").build();
    }

    /**
     * Researches destination options.
     *
     * <p>Uses OpenClaw's LLM API to research travel destinations with
     * costs and ratings.
     */
    private Worker destinationResearchWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("travel-agent"))
                .systemPrompt("""
                        You are a travel planning agent. Research destination options with
                        costs and ratings.""")
                .responseSchema(DestinationResearchResult.class)
                .build();

        return Worker.builder()
                .name("destination-research-agent")
                .capabilities(List.of(cap("destination-research")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    /**
     * Searches available flights for selected destination.
     *
     * <p>Uses OpenClaw's LLM API to search for flights with airline,
     * price, and number of stops.
     */
    private Worker flightSearchWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("travel-agent"))
                .systemPrompt("""
                        You are a travel planning agent. Search for flights with airline,
                        price, and number of stops.""")
                .responseSchema(FlightSearchResult.class)
                .build();

        return Worker.builder()
                .name("flight-search-agent")
                .capabilities(List.of(cap("flight-search")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    /**
     * Searches available hotels for selected destination.
     *
     * <p>Uses OpenClaw's LLM API to search for hotels with name,
     * price, and rating.
     */
    private Worker hotelSearchWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("travel-agent"))
                .systemPrompt("""
                        You are a travel planning agent. Search for hotels with name,
                        price, and rating.""")
                .responseSchema(HotelSearchResult.class)
                .build();

        return Worker.builder()
                .name("hotel-search-agent")
                .capabilities(List.of(cap("hotel-search")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    /**
     * Assesses total cost.
     *
     * <p>Uses OpenClaw's LLM API to assess the total travel budget
     * and determine if approval is required.
     */
    private Worker budgetAssessmentWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("travel-agent"))
                .systemPrompt("""
                        You are a travel planning agent. Assess the total travel budget
                        and determine if approval is required.""")
                .responseSchema(BudgetAssessmentResult.class)
                .build();

        return Worker.builder()
                .name("budget-assessment-agent")
                .capabilities(List.of(cap("budget-assessment")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    /**
     * Books selected flights and hotels.
     *
     * <p>Uses OpenClaw's LLM API to book the selected flights and hotels.
     * If booking fails, sets declined=true with a reason.
     */
    private Worker bookingWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("travel-agent"))
                .systemPrompt("""
                        You are a travel planning agent. Book the selected flights and hotels.
                        If booking fails, set declined=true with a reason.""")
                .responseSchema(TravelBookingResult.class)
                .build();

        return Worker.builder()
                .name("booking-agent")
                .capabilities(List.of(cap("booking")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    /**
     * Rebooks with alternative dates after a DECLINE.
     *
     * <p>Uses OpenClaw's LLM API to rebook after a declined booking,
     * finding alternative dates.
     */
    private Worker rebookingWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("travel-agent"))
                .systemPrompt("""
                        You are a travel planning agent. Rebook after a declined booking,
                        finding alternative dates.""")
                .responseSchema(RebookingResult.class)
                .build();

        return Worker.builder()
                .name("rebooking-agent")
                .capabilities(List.of(cap("rebooking")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    /**
     * Confirms booking and sends itinerary.
     *
     * <p>Uses OpenClaw's LLM API to confirm the travel itinerary and
     * send confirmation details.
     */
    private Worker confirmationWorker() {
        final Agent agent = Agent.builder()
                .model(openClawFactory.forAgent("travel-agent"))
                .systemPrompt("""
                        You are a travel planning agent. Confirm the travel itinerary and
                        send confirmation details.""")
                .responseSchema(ConfirmationResult.class)
                .build();

        return Worker.builder()
                .name("confirmation-agent")
                .capabilities(List.of(cap("confirmation")))
                .function(new AgentWorkerFunction(agent))
                .build();
    }

    private AgentDescriptor travelDescriptor() {
        return new AgentDescriptor(
                "openclaw:travel-agent@1",       // agentId
                "OpenClaw Travel Agent",         // name
                "1",                             // version
                "openclaw",                      // provider
                "openclaw",                      // modelFamily
                null,                            // modelVersion
                null,                            // weightsFingerprint
                null,                            // domainVocabulary
                null,                            // slotVocabulary
                null,                            // dispositionVocabulary
                null,                            // axisVocabularies
                "casehubio/life/travel",         // slot
                List.of(),                       // capabilities
                null,                            // disposition
                "GB",                            // jurisdiction
                null,                            // dataHandlingPolicy
                tenancyId,                       // tenancyId
                "Travel planning and booking agent"  // briefing
        );
    }
}
