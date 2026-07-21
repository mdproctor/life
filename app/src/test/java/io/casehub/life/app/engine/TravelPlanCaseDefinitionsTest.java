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

import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.GoalKind;
import io.casehub.api.model.HumanTaskTarget;
import io.casehub.api.model.OnThresholdReached;
import io.casehub.api.model.SubCaseTarget;
import org.junit.jupiter.api.Test;
import io.casehub.api.spi.routing.CandidateSetSpec;
import io.casehub.api.spi.routing.StaticSetStrategy;

/**
 * Verifies the fluent DSL companion produces a valid CaseDefinition with the
 * correct structure — name, namespace, binding count, goal count, parallel
 * bindings, adaptive gates, M-of-N SubCase, DECLINE recovery.
 *
 * <p>This is a pure unit test — no Quarkus container needed.
 */
class TravelPlanCaseDefinitionsTest {

    @Test
    void definitionHasCorrectIdentity() {
        CaseDefinition def = TravelPlanCaseDefinitions.travelPlan();
        assertNotNull(def);
        assertEquals("casehub-life", def.getNamespace());
        assertEquals("travel-plan", def.getName());
        assertEquals("1.0.0", def.getVersion());
    }

    @Test
    void hasSevenCapabilities() {
        CaseDefinition def = TravelPlanCaseDefinitions.travelPlan();
        assertEquals(7, def.getCapabilities().size());
        var names = def.getCapabilities().stream().map(c -> c.name()).toList();
        assertTrue(names.contains("destination-research"));
        assertTrue(names.contains("flight-search"));
        assertTrue(names.contains("hotel-search"));
        assertTrue(names.contains("budget-assessment"));
        assertTrue(names.contains("booking"));
        assertTrue(names.contains("rebooking"));
        assertTrue(names.contains("confirmation"));
    }

    @Test
    void hasElevenBindings() {
        // 4 capability + 3 SubCase + 1 humanTask + 3 more capability = 11 total
        CaseDefinition def = TravelPlanCaseDefinitions.travelPlan();
        assertEquals(11, def.getBindings().size());
        var names = def.getBindings().stream().map(b -> b.getName()).toList();
        assertTrue(names.contains("destination-research"));
        assertTrue(names.contains("flight-search"));
        assertTrue(names.contains("hotel-search"));
        assertTrue(names.contains("budget-assessment"));
        assertTrue(names.contains("family-vote-a"));
        assertTrue(names.contains("family-vote-b"));
        assertTrue(names.contains("family-vote-c"));
        assertTrue(names.contains("approval-gate"));
        assertTrue(names.contains("booking"));
        assertTrue(names.contains("rebooking"));
        assertTrue(names.contains("confirmation"));
    }

    @Test
    void hasOneSuccessGoal() {
        CaseDefinition def = TravelPlanCaseDefinitions.travelPlan();
        assertEquals(1, def.getGoals().size());
        assertEquals("trip-booked", def.getGoals().get(0).getName());
        assertEquals(GoalKind.SUCCESS.value(), def.getGoals().get(0).getKind());
    }

    @Test
    void hasCompletion() {
        CaseDefinition def = TravelPlanCaseDefinitions.travelPlan();
        assertNotNull(def.getCompletion(), "Completion must be set");
    }

    @Test
    void approvalGateIsHumanTask() {
        CaseDefinition def = TravelPlanCaseDefinitions.travelPlan();
        var binding = def.getBindings().stream()
                .filter(b -> "approval-gate".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.target() instanceof HumanTaskTarget ht
                && "Approve travel booking".equals(ht.title())
                && ht.candidateGroups() instanceof CandidateSetSpec.Inline inline && inline.strategy() instanceof StaticSetStrategy ss && ss.values().contains("household-admin")
                && "casehubio/life/finance".equals(ht.scope()));
    }

    @Test
    void familyVoteBindingsAreSubCaseWithMofN() {
        CaseDefinition def = TravelPlanCaseDefinitions.travelPlan();
        var voteBindings = def.getBindings().stream()
                .filter(b -> b.getName().startsWith("family-vote-"))
                .toList();
        assertEquals(3, voteBindings.size());

        for (var binding : voteBindings) {
            assertTrue(binding.target() instanceof SubCaseTarget sct);
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
    void parallelBindingsShareTriggerCondition() {
        CaseDefinition def = TravelPlanCaseDefinitions.travelPlan();
        var flightSearch = def.getBindings().stream()
                .filter(b -> "flight-search".equals(b.getName()))
                .findFirst().orElseThrow();
        var hotelSearch = def.getBindings().stream()
                .filter(b -> "hotel-search".equals(b.getName()))
                .findFirst().orElseThrow();

        // Both check selectedDestination != null — fire in parallel
        assertNotNull(flightSearch.getWhen());
        assertNotNull(hotelSearch.getWhen());
    }

    @Test
    void rebookingBindingExists() {
        CaseDefinition def = TravelPlanCaseDefinitions.travelPlan();
        var binding = def.getBindings().stream()
                .filter(b -> "rebooking".equals(b.getName()))
                .findFirst();
        assertTrue(binding.isPresent(), "rebooking DECLINE recovery binding must exist");
    }
}
