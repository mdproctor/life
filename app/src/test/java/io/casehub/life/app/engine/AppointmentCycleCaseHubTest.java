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
import io.casehub.api.spi.routing.CandidateSetSpec;
import io.casehub.api.spi.routing.StaticSetStrategy;
import io.casehub.life.api.LifeCaseType;
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
                .stream().map(c -> c.name()).toList();
        assertEquals(6, names.size());
        assertTrue(names.containsAll(List.of(
                "book-appointment", "find-alternative", "confirm-appointment",
                "pre-visit-prep", "record-health-decision", "follow-up-sentinel")));
    }

    @Test
    void hasSixBindings() {
        var names = caseHub.getDefinition().getBindings()
                .stream().map(b -> b.getName()).toList();
        assertEquals(8, names.size());
        assertTrue(names.containsAll(List.of(
                "book-appointment", "find-alternative", "confirm-appointment",
                "pre-visit-prep", "attend-and-record", "record-health-decision",
                "follow-up-sentinel", "sentinel-escalation")));
    }

    @Test
    void attendAndRecordIsHumanTask() {
        var binding = caseHub.getDefinition().getBindings().stream()
                .filter(b -> "attend-and-record".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.target() instanceof HumanTaskTarget ht
                && "Record post-visit notes".equals(ht.title())
                && ht.candidateGroups() instanceof CandidateSetSpec.Inline inline && inline.strategy() instanceof StaticSetStrategy ss && ss.values().contains("household-member")
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
        var names = Set.copyOf(workers.stream().map(w -> w.name()).toList());
        assertEquals(Set.of(
                "book-appointment-agent", "find-alternative-agent",
                "confirm-appointment-agent", "pre-visit-prep-agent",
                "record-health-decision-agent"), names);
    }

    @Test
    void caseDefinitionHasAgentDescriptor() {
        // After engine#543, AgentDescriptor moved from Worker to CaseDefinition.agentDescriptors.
        // This test enforces the architectural requirement: every LLM-backed worker must
        // have a corresponding AgentDescriptor registered on the CaseDefinition so the trust
        // system and attestation pipeline can attribute outcomes to the correct agent.
        final var def = caseHub.getDefinition();

        assertThat(def.agentDescriptorFor(LifeAgent.HEALTH.agentId()))
                .as("CaseDefinition must have agentDescriptor for " + LifeAgent.HEALTH.agentId())
                .isPresent();

        final var descriptor = def.agentDescriptorFor(LifeAgent.HEALTH.agentId()).orElseThrow();
        assertThat(descriptor.agentId())
                .as("agentId must follow {model-family}:{persona}@{major} convention")
                .isEqualTo(LifeAgent.HEALTH.agentId());
        assertThat(descriptor.provider()).isEqualTo(LifeAgent.MODEL_FAMILY);
        assertThat(descriptor.slot()).isEqualTo(LifeAgent.HEALTH.slot());
    }

    @Test
    void lifeCaseType() {
        assertEquals(LifeCaseType.APPOINTMENT_CYCLE, caseHub.lifeCaseType());
    }
}
