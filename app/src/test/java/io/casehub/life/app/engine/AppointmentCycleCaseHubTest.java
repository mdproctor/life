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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.casehub.api.model.HumanTaskTarget;
import io.casehub.api.model.evaluator.JQExpressionEvaluator;
import io.casehub.api.model.evaluator.ListEvaluator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/**
 * Unit-level integration test for the appointment-cycle CaseHub definition.
 *
 * <p>Verifies that the YAML loads correctly and the augmented workers are present.
 * Does not start a case — that is covered by {@link AppointmentCycleIntegrationTest}.
 *
 * <p>TestLifeOpenClawChatModelProvider is active via quarkus.arc.selected-alternatives.
 * This avoids @InjectMock which would force a Quarkus CDI restart between test classes,
 * causing codec re-registration failures (BlackboardEventCodecRegistrar).
 */
@QuarkusTest
class AppointmentCycleCaseHubTest {

    @Inject
    AppointmentCycleCaseHub caseHub;

    @Test
    void definitionLoads() {
        var def = caseHub.getDefinition();
        assertNotNull(def);
        assertEquals("casehub-life", def.getNamespace());
        assertEquals("appointment-cycle", def.getName());
        assertEquals("1.0.0", def.getVersion());
    }

    @Test
    void hasFiveCapabilities() {
        var names = caseHub.getDefinition().getCapabilities()
                .stream().map(c -> c.getName()).toList();
        assertEquals(5, names.size());
        assertTrue(names.containsAll(List.of(
                "book-appointment", "find-alternative", "confirm-appointment",
                "pre-visit-prep", "record-health-decision")));
    }

    @Test
    void hasSixBindings() {
        var names = caseHub.getDefinition().getBindings()
                .stream().map(b -> b.getName()).toList();
        assertEquals(6, names.size());
        assertTrue(names.containsAll(List.of(
                "book-appointment", "find-alternative", "confirm-appointment",
                "pre-visit-prep", "attend-and-record", "record-health-decision")));
    }

    @Test
    void attendAndRecordIsHumanTask() {
        var binding = caseHub.getDefinition().getBindings().stream()
                .filter(b -> "attend-and-record".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.target() instanceof HumanTaskTarget ht
                && "Record post-visit notes".equals(ht.title())
                && ht.candidateGroups() instanceof ListEvaluator.StaticList sl && sl.values().contains("household-member")
                && "casehubio/life/health".equals(ht.scope()));
    }

    @Test
    void hasAppointmentCompleteGoal() {
        var goals = caseHub.getDefinition().getGoals();
        assertEquals(1, goals.size());
        assertEquals("appointment-complete", goals.get(0).getName());
        assertTrue(goals.get(0).getCondition() instanceof JQExpressionEvaluator jq
                        && jq.expression().contains("healthDecisionRecorded"),
                "Goal condition should check healthDecisionRecorded");
    }

    @Test
    void hasFiveWorkers() {
        var workers = caseHub.getDefinition().getWorkers();
        assertEquals(5, workers.size(), "Exactly 5 workers expected — size catches double-augmentation");
        var names = Set.copyOf(workers.stream().map(w -> w.getName()).toList());
        assertEquals(Set.of(
                "book-appointment-agent", "find-alternative-agent",
                "confirm-appointment-agent", "pre-visit-prep-agent",
                "record-health-decision-agent"), names);
    }

    @Test
    void bookAppointmentWorkerHasAgentDescriptor() {
        // Worker.Builder.build() does not enforce agentDescriptor — it is silently nullable.
        // This test enforces the architectural requirement: every LLM-backed worker must
        // carry an AgentDescriptor so the trust system and attestation pipeline can
        // attribute outcomes to the correct agent.
        final var worker = caseHub.getDefinition().getWorkers().stream()
                .filter(w -> "book-appointment-agent".equals(w.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("book-appointment-agent not found"));

        assertThat(worker.agentDescriptor())
                .as("book-appointment-agent must carry an AgentDescriptor")
                .isNotNull();
        assertThat(worker.agentDescriptor().agentId())
                .as("agentId must follow {model-family}:{persona}@{major} convention")
                .isEqualTo("openclaw:health-agent@1");
        assertThat(worker.agentDescriptor().provider()).isEqualTo("openclaw");
        assertThat(worker.agentDescriptor().slot()).isEqualTo("casehubio/life/health");
    }
}
