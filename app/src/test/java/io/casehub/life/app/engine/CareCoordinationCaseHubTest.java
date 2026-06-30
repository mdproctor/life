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
import io.casehub.life.api.LifeCaseType;
import io.casehub.api.model.SubCaseTarget;
import io.casehub.api.model.evaluator.JQExpressionEvaluator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import io.casehub.api.model.evaluator.ListEvaluator;

/**
 * Definition test for the care-coordination CaseHub.
 *
 * <p>Verifies that the YAML loads correctly, augmented workers are present,
 * milestones are defined, SubCase binding exists, and adaptive escalation
 * has correct conditions.
 */
@QuarkusTest
class CareCoordinationCaseHubTest {

    @Inject
    CareCoordinationCaseHub caseHub;

    @Test
    void definitionLoads() {
        var def = caseHub.getDefinition();
        assertNotNull(def);
        assertEquals("casehub-life", def.getNamespace());
        assertEquals("care-coordination", def.getName());
        assertEquals("1.0.0", def.getVersion());
    }

    @Test
    void hasThreeCapabilities() {
        var names = caseHub.getDefinition().getCapabilities()
                .stream().map(c -> c.name()).toList();
        assertEquals(4, names.size());
        assertTrue(names.containsAll(List.of(
                "needs-assessment", "care-plan", "health-check", "care-quality-sentinel")));
    }

    @Test
    void hasSevenBindings() {
        var names = caseHub.getDefinition().getBindings()
                .stream().map(b -> b.getName()).toList();
        assertEquals(9, names.size());
        assertTrue(names.containsAll(List.of(
                "needs-assessment", "care-plan", "assign-carer",
                "care-episode", "health-check", "escalate-concern",
                "care-review", "care-quality-sentinel", "sentinel-escalation")));
    }

    @Test
    void hasTwoMilestones() {
        var milestones = caseHub.getDefinition().getMilestones();
        assertEquals(2, milestones.size());

        var names = milestones.stream().map(m -> m.getName()).toList();
        assertTrue(names.containsAll(List.of("assessment-complete", "carer-assigned")));

        var assessmentMilestone = milestones.stream()
                .filter(m -> "assessment-complete".equals(m.getName()))
                .findFirst().orElseThrow();
        assertTrue(assessmentMilestone.getCompletionCriteria() instanceof JQExpressionEvaluator jq
                && jq.expression().contains("assessment"));

        var carerMilestone = milestones.stream()
                .filter(m -> "carer-assigned".equals(m.getName()))
                .findFirst().orElseThrow();
        assertTrue(carerMilestone.getCompletionCriteria() instanceof JQExpressionEvaluator jq
                && jq.expression().contains("carerAssignment"));
    }

    @Test
    void assignCarerIsHumanTask() {
        var binding = caseHub.getDefinition().getBindings().stream()
                .filter(b -> "assign-carer".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.target() instanceof HumanTaskTarget ht
                && "Accept care delegation".equals(ht.title())
                && ht.candidateGroups() instanceof ListEvaluator.StaticList sl && sl.values().contains("household-member")
                && "casehubio/life/elder-care".equals(ht.scope()));
    }

    @Test
    void careEpisodeIsSubCase() {
        var binding = caseHub.getDefinition().getBindings().stream()
                .filter(b -> "care-episode".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.target() instanceof SubCaseTarget,
                "care-episode binding should be SubCaseTarget");
        var subCase = ((SubCaseTarget) binding.target()).subCase();
        assertEquals("casehub-life", subCase.namespace());
        assertEquals("care-episode", subCase.name());
        assertEquals("1.0.0", subCase.version());
        assertTrue(subCase.waitForCompletion());
        assertEquals("{ careRequest: .careRequest, carePlan: .carePlan }", subCase.inputMapping());
        assertEquals("{ episodeResult: . }", subCase.outputMapping());
    }

    @Test
    void escalateConcernIsAdaptive() {
        var binding = caseHub.getDefinition().getBindings().stream()
                .filter(b -> "escalate-concern".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        // Adaptive: fires only when health concern detected
        assertTrue(binding.getWhen() instanceof JQExpressionEvaluator jq
                && jq.expression().contains("healthConcern == true"));
        assertTrue(binding.target() instanceof HumanTaskTarget ht
                && ht.candidateGroups() instanceof ListEvaluator.StaticList sl && sl.values().contains("household-admin")
                && "casehubio/life/elder-care".equals(ht.scope()));
    }

    @Test
    void careReviewIsHumanTask() {
        var binding = caseHub.getDefinition().getBindings().stream()
                .filter(b -> "care-review".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.target() instanceof HumanTaskTarget ht
                && "Review care quality".equals(ht.title())
                && ht.candidateGroups() instanceof ListEvaluator.StaticList sl && sl.values().contains("household-admin")
                && "casehubio/life/elder-care".equals(ht.scope()));
    }

    @Test
    void hasReviewCompleteGoal() {
        var goals = caseHub.getDefinition().getGoals();
        assertEquals(1, goals.size());
        assertEquals("review-complete", goals.get(0).getName());
        assertTrue(goals.get(0).getCondition() instanceof JQExpressionEvaluator jq
                        && jq.expression().contains("careReview"),
                "Goal condition should check careReview");
    }

    @Test
    void hasThreeWorkers() {
        var workers = caseHub.getDefinition().getWorkers();
        assertEquals(3, workers.size(), "Exactly 3 workers expected — size catches double-augmentation");
        var names = Set.copyOf(workers.stream().map(w -> w.name()).toList());
        assertEquals(Set.of(
                "needs-assessment-agent", "care-plan-agent",
                "health-check-agent"), names);
    }

    @Test
    void lifeCaseType() {
        assertEquals(LifeCaseType.CARE_COORDINATION, caseHub.lifeCaseType());
    }
}
