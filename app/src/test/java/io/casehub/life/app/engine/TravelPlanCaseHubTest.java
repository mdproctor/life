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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.casehub.api.model.HumanTaskTarget;
import io.casehub.api.model.OnThresholdReached;
import io.casehub.api.model.SubCaseTarget;
import io.casehub.api.model.evaluator.JQExpressionEvaluator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/**
 * Definition test for the travel-plan CaseHub.
 *
 * <p>Verifies that the YAML loads correctly, augmented workers and M-of-N SubCase
 * bindings are present, parallel bindings share the same trigger condition,
 * and adaptive gates have correct conditions.
 */
@QuarkusTest
class TravelPlanCaseHubTest {

    @Inject
    TravelPlanCaseHub caseHub;

    @Test
    void definitionLoads() {
        var def = caseHub.getDefinition();
        assertNotNull(def);
        assertEquals("casehub-life", def.getNamespace());
        assertEquals("travel-plan", def.getName());
        assertEquals("1.0.0", def.getVersion());
    }

    @Test
    void hasSevenCapabilities() {
        var names = caseHub.getDefinition().getCapabilities()
                .stream().map(c -> c.getName()).toList();
        assertEquals(7, names.size());
        assertTrue(names.containsAll(List.of(
                "destination-research", "flight-search", "hotel-search",
                "budget-assessment", "booking", "rebooking", "confirmation")));
    }

    @Test
    void hasElevenBindings() {
        // 8 from YAML + 3 SubCase from augmentation
        var names = caseHub.getDefinition().getBindings()
                .stream().map(b -> b.getName()).toList();
        assertEquals(11, names.size());
        assertTrue(names.containsAll(List.of(
                "destination-research", "flight-search", "hotel-search",
                "budget-assessment", "approval-gate", "booking", "rebooking",
                "confirmation", "family-vote-a", "family-vote-b", "family-vote-c")));
    }

    @Test
    void hasTripBookedGoal() {
        var goals = caseHub.getDefinition().getGoals();
        assertEquals(1, goals.size());
        assertEquals("trip-booked", goals.get(0).getName());
        assertTrue(goals.get(0).getCondition() instanceof JQExpressionEvaluator jq
                        && jq.expression().contains("confirmation"),
                "Goal condition should check confirmation");
    }

    @Test
    void hasSevenWorkers() {
        var workers = caseHub.getDefinition().getWorkers();
        assertEquals(7, workers.size(), "Exactly 7 workers expected — size catches double-augmentation");
        var names = Set.copyOf(workers.stream().map(w -> w.getName()).toList());
        assertEquals(Set.of(
                "destination-research-agent", "flight-search-agent",
                "hotel-search-agent", "budget-assessment-agent",
                "booking-agent", "rebooking-agent", "confirmation-agent"), names);
    }

    @Test
    void flightAndHotelSearchAreParallel() {
        var bindings = caseHub.getDefinition().getBindings();
        var flightSearch = bindings.stream()
                .filter(b -> "flight-search".equals(b.getName()))
                .findFirst().orElseThrow();
        var hotelSearch = bindings.stream()
                .filter(b -> "hotel-search".equals(b.getName()))
                .findFirst().orElseThrow();

        // Both fire on selectedDestination — parallel execution
        assertTrue(flightSearch.getWhen() instanceof JQExpressionEvaluator flightJq
                && flightJq.expression().contains("selectedDestination"));
        assertTrue(hotelSearch.getWhen() instanceof JQExpressionEvaluator hotelJq
                && hotelJq.expression().contains("selectedDestination"));
    }

    @Test
    void approvalGateIsHumanTask() {
        var binding = caseHub.getDefinition().getBindings().stream()
                .filter(b -> "approval-gate".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.target() instanceof HumanTaskTarget ht
                && "Approve travel booking".equals(ht.title())
                && ht.candidateGroups().contains("household-admin")
                && "casehubio/life/finance".equals(ht.scope()));
    }

    @Test
    void approvalGateIsAdaptive() {
        var binding = caseHub.getDefinition().getBindings().stream()
                .filter(b -> "approval-gate".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        // Adaptive: fires only for mid-range budget (requiresApproval true, isHighValue false)
        assertTrue(binding.getWhen() instanceof JQExpressionEvaluator jq
                && jq.expression().contains("requiresApproval == true")
                && jq.expression().contains("isHighValue == false"));
    }

    @Test
    void familyVoteBindingsAreSubCaseWithMofN() {
        var bindings = caseHub.getDefinition().getBindings();
        var voteBindings = bindings.stream()
                .filter(b -> b.getName().startsWith("family-vote-"))
                .toList();
        assertEquals(3, voteBindings.size(), "Expected 3 family-vote SubCase bindings");

        for (var binding : voteBindings) {
            assertTrue(binding.target() instanceof SubCaseTarget sct,
                    binding.getName() + " should be SubCaseTarget");
            var subCase = ((SubCaseTarget) binding.target()).subCase();
            assertEquals("life", subCase.namespace());
            assertEquals("family-vote", subCase.name());
            assertEquals("1.0.0", subCase.version());
            assertEquals("family-vote", subCase.groupId());
            assertEquals(3, subCase.totalInGroup());
            assertEquals(2, subCase.requiredCount());
            assertEquals(OnThresholdReached.KEEP, subCase.onThresholdReached());
        }
    }

    @Test
    void familyVoteBindingsAreAdaptive() {
        var bindings = caseHub.getDefinition().getBindings();
        var voteA = bindings.stream()
                .filter(b -> "family-vote-a".equals(b.getName()))
                .findFirst().orElseThrow();
        // Adaptive: fires only for high-value budget
        assertTrue(voteA.getWhen() instanceof JQExpressionEvaluator jq
                && jq.expression().contains("isHighValue == true"));
    }

    @Test
    void rebookingBindingFiresOnDecline() {
        var binding = caseHub.getDefinition().getBindings().stream()
                .filter(b -> "rebooking".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.getWhen() instanceof JQExpressionEvaluator jq
                && jq.expression().contains("booking.declined == true"));
    }
}
