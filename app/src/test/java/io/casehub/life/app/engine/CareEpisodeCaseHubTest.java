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
 * Definition test for the care-episode CaseHub (child case).
 *
 * <p>Verifies that the YAML loads correctly and the augmented workers are present.
 * This case is only spawned as a sub-case by care-coordination — not used directly
 * by LifeCaseService.
 */
@QuarkusTest
class CareEpisodeCaseHubTest {

    @Inject
    CareEpisodeCaseHub caseHub;

    @Test
    void definitionLoads() {
        var def = caseHub.getDefinition();
        assertNotNull(def);
        assertEquals("casehub-life", def.getNamespace());
        assertEquals("care-episode", def.getName());
        assertEquals("1.0.0", def.getVersion());
    }

    @Test
    void hasTwoCapabilities() {
        var names = caseHub.getDefinition().getCapabilities()
                .stream().map(c -> c.getName()).toList();
        assertEquals(2, names.size());
        assertTrue(names.containsAll(List.of(
                "assess-patient", "provide-care")));
    }

    @Test
    void hasThreeBindings() {
        var names = caseHub.getDefinition().getBindings()
                .stream().map(b -> b.getName()).toList();
        assertEquals(3, names.size());
        assertTrue(names.containsAll(List.of(
                "assess-patient", "provide-care", "record-notes")));
    }

    @Test
    void recordNotesIsHumanTask() {
        var binding = caseHub.getDefinition().getBindings().stream()
                .filter(b -> "record-notes".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.target() instanceof HumanTaskTarget ht
                && "Record care visit notes".equals(ht.title())
                && ht.candidateGroups() instanceof ListEvaluator.StaticList sl && sl.values().contains("household-member")
                && "casehubio/life/elder-care".equals(ht.scope()));
    }

    @Test
    void hasEpisodeCompleteGoal() {
        var goals = caseHub.getDefinition().getGoals();
        assertEquals(1, goals.size());
        assertEquals("episode-complete", goals.get(0).getName());
        assertTrue(goals.get(0).getCondition() instanceof JQExpressionEvaluator jq
                        && jq.expression().contains("careNotes"),
                "Goal condition should check careNotes");
    }

    @Test
    void hasTwoWorkers() {
        var workers = caseHub.getDefinition().getWorkers();
        assertEquals(2, workers.size(), "Exactly 2 workers expected — size catches double-augmentation");
        var names = Set.copyOf(workers.stream().map(w -> w.getName()).toList());
        assertEquals(Set.of(
                "assess-patient-agent", "provide-care-agent"), names);
    }
}
