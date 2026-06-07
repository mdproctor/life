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
import io.casehub.api.model.evaluator.JQExpressionEvaluator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import io.casehub.api.model.evaluator.ListEvaluator;

/**
 * Definition test for the home-maintenance CaseHub.
 *
 * <p>Verifies that the YAML loads correctly and the augmented workers are present.
 */
@QuarkusTest
class HomeMaintenanceCaseHubTest {

    @Inject
    HomeMaintenanceCaseHub caseHub;

    @Test
    void definitionLoads() {
        var def = caseHub.getDefinition();
        assertNotNull(def);
        assertEquals("casehub-life", def.getNamespace());
        assertEquals("home-maintenance", def.getName());
        assertEquals("1.0.0", def.getVersion());
    }

    @Test
    void hasFiveCapabilities() {
        var names = caseHub.getDefinition().getCapabilities()
                .stream().map(c -> c.getName()).toList();
        assertEquals(5, names.size());
        assertTrue(names.containsAll(List.of(
                "schedule-inspection", "get-quotes", "issue-commitment",
                "monitor-job", "record-completion")));
    }

    @Test
    void hasSevenBindings() {
        var names = caseHub.getDefinition().getBindings()
                .stream().map(b -> b.getName()).toList();
        assertEquals(7, names.size());
        assertTrue(names.containsAll(List.of(
                "schedule-inspection", "get-quotes", "approve-contractor",
                "issue-commitment", "monitor-job", "verify-completion",
                "record-completion")));
    }

    @Test
    void approveContractorIsHumanTask() {
        var binding = caseHub.getDefinition().getBindings().stream()
                .filter(b -> "approve-contractor".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.target() instanceof HumanTaskTarget ht
                && "Select a contractor quote".equals(ht.title())
                && ht.candidateGroups() instanceof ListEvaluator.StaticList sl && sl.values().contains("household-admin")
                && "casehubio/life/household".equals(ht.scope()));
    }

    @Test
    void verifyCompletionIsHumanTask() {
        var binding = caseHub.getDefinition().getBindings().stream()
                .filter(b -> "verify-completion".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.target() instanceof HumanTaskTarget ht
                && "Verify job completion".equals(ht.title())
                && ht.candidateGroups() instanceof ListEvaluator.StaticList sl && sl.values().contains("household-member")
                && "casehubio/life/household".equals(ht.scope()));
    }

    @Test
    void hasJobCompleteGoal() {
        var goals = caseHub.getDefinition().getGoals();
        assertEquals(1, goals.size());
        assertEquals("job-complete", goals.get(0).getName());
        assertTrue(goals.get(0).getCondition() instanceof JQExpressionEvaluator jq
                        && jq.expression().contains("completionRecord"),
                "Goal condition should check completionRecord");
    }

    @Test
    void hasFiveWorkers() {
        var workers = caseHub.getDefinition().getWorkers();
        assertEquals(5, workers.size(), "Exactly 5 workers expected — size catches double-augmentation");
        var names = Set.copyOf(workers.stream().map(w -> w.getName()).toList());
        assertEquals(Set.of(
                "schedule-inspection-agent", "get-quotes-agent",
                "issue-commitment-agent", "monitor-job-agent",
                "record-completion-agent"), names);
    }
}
