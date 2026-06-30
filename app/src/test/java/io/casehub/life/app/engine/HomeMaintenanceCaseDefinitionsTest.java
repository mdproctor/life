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
import io.casehub.api.model.HumanTaskTarget;
import io.casehub.api.model.evaluator.JQExpressionEvaluator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import io.casehub.api.spi.routing.CandidateSetSpec;
import io.casehub.api.spi.routing.StaticSetStrategy;

/**
 * DSL test for the home-maintenance case definition.
 *
 * <p>Verifies that the fluent DSL companion produces a structurally equivalent
 * definition to the YAML: same name, namespace, bindings, goals, capabilities,
 * and humanTask targets.
 */
class HomeMaintenanceCaseDefinitionsTest {

    private final CaseDefinition def = HomeMaintenanceCaseDefinitions.homeMaintenance();

    @Test
    void definitionMetadata() {
        assertNotNull(def);
        assertEquals("casehub-life", def.getNamespace());
        assertEquals("home-maintenance", def.getName());
        assertEquals("1.0.0", def.getVersion());
    }

    @Test
    void hasFiveCapabilities() {
        var names = def.getCapabilities()
                .stream().map(c -> c.name()).toList();
        assertEquals(5, names.size());
        assertTrue(names.containsAll(List.of(
                "schedule-inspection", "get-quotes", "issue-commitment",
                "monitor-job", "record-completion")));
    }

    @Test
    void hasSevenBindings() {
        var names = def.getBindings()
                .stream().map(b -> b.getName()).toList();
        assertEquals(7, names.size());
        assertTrue(names.containsAll(List.of(
                "schedule-inspection", "get-quotes", "approve-contractor",
                "issue-commitment", "monitor-job", "verify-completion",
                "record-completion")));
    }

    @Test
    void approveContractorIsHumanTask() {
        var binding = def.getBindings().stream()
                .filter(b -> "approve-contractor".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.target() instanceof HumanTaskTarget ht
                && "Select a contractor quote".equals(ht.title())
                && ht.candidateGroups() instanceof CandidateSetSpec.Inline inline && inline.strategy() instanceof StaticSetStrategy ss && ss.values().contains("household-admin")
                && "casehubio/life/household".equals(ht.scope()));
    }

    @Test
    void verifyCompletionIsHumanTask() {
        var binding = def.getBindings().stream()
                .filter(b -> "verify-completion".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.target() instanceof HumanTaskTarget ht
                && "Verify job completion".equals(ht.title())
                && ht.candidateGroups() instanceof CandidateSetSpec.Inline inline && inline.strategy() instanceof StaticSetStrategy ss && ss.values().contains("household-member")
                && "casehubio/life/household".equals(ht.scope()));
    }

    @Test
    void hasJobCompleteGoal() {
        var goals = def.getGoals();
        assertEquals(1, goals.size());
        assertEquals("job-complete", goals.get(0).getName());
        assertTrue(goals.get(0).getCondition() instanceof JQExpressionEvaluator jq
                        && jq.expression().contains("completionRecord"),
                "Goal condition should check completionRecord");
    }

    @Test
    void monitorJobBindingChecksChannelMessage() {
        var binding = def.getBindings().stream()
                .filter(b -> "monitor-job".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.getWhen() instanceof JQExpressionEvaluator jq
                        && jq.expression().contains("channelMessage")
                        && jq.expression().contains("RESPONSE"),
                "monitor-job binding should check .channelMessage.messageType == RESPONSE");
    }
}
