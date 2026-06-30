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
import io.casehub.life.api.LifeCaseType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import io.casehub.api.model.evaluator.ListEvaluator;

/**
 * Definition test for the contractor-coordination CaseHub.
 *
 * <p>Verifies that the YAML loads correctly and the augmented workers are present.
 */
@QuarkusTest
class ContractorCoordinationCaseHubTest {

    @Inject
    ContractorCoordinationCaseHub caseHub;

    @Test
    void definitionLoads() {
        var def = caseHub.getDefinition();
        assertNotNull(def);
        assertEquals("casehub-life", def.getNamespace());
        assertEquals("contractor-coordination", def.getName());
        assertEquals("1.0.0", def.getVersion());
    }

    @Test
    void hasFiveCapabilities() {
        var names = caseHub.getDefinition().getCapabilities()
                .stream().map(c -> c.name()).toList();
        assertEquals(6, names.size());
        assertTrue(names.containsAll(List.of(
                "request-quote", "watchdog-escalation", "quote-received",
                "job-monitoring", "record-payment", "contractor-sentinel")));
    }

    @Test
    void hasSevenBindings() {
        var names = caseHub.getDefinition().getBindings()
                .stream().map(b -> b.getName()).toList();
        assertEquals(9, names.size());
        assertTrue(names.containsAll(List.of(
                "request-quote", "watchdog-escalation", "quote-received",
                "approve-quote", "job-monitoring", "payment-gate",
                "record-payment", "contractor-sentinel", "sentinel-escalation")));
    }

    @Test
    void approveQuoteIsHumanTask() {
        var binding = caseHub.getDefinition().getBindings().stream()
                .filter(b -> "approve-quote".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.target() instanceof HumanTaskTarget ht
                && "Approve contractor quote".equals(ht.title())
                && ht.candidateGroups() instanceof ListEvaluator.StaticList sl && sl.values().contains("household-admin")
                && "casehubio/life/household".equals(ht.scope()));
    }

    @Test
    void paymentGateIsHumanTask() {
        var binding = caseHub.getDefinition().getBindings().stream()
                .filter(b -> "payment-gate".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.target() instanceof HumanTaskTarget ht
                && "Confirm contractor payment".equals(ht.title())
                && ht.candidateGroups() instanceof ListEvaluator.StaticList sl && sl.values().contains("household-admin")
                && "casehubio/life/finance".equals(ht.scope()));
    }

    @Test
    void hasContractorPaidGoal() {
        var goals = caseHub.getDefinition().getGoals();
        assertEquals(1, goals.size());
        assertEquals("contractor-paid", goals.get(0).getName());
        assertTrue(goals.get(0).getCondition() instanceof JQExpressionEvaluator jq
                        && jq.expression().contains("paymentRecorded"),
                "Goal condition should check paymentRecorded");
    }

    @Test
    void hasFiveWorkers() {
        var workers = caseHub.getDefinition().getWorkers();
        assertEquals(5, workers.size(), "Exactly 5 workers expected — size catches double-augmentation");
        var names = Set.copyOf(workers.stream().map(w -> w.name()).toList());
        assertEquals(Set.of(
                "request-quote-agent", "watchdog-escalation-agent",
                "quote-received-agent", "job-monitoring-agent",
                "record-payment-agent"), names);
    }

    @Test
    void watchdogEscalationIsAdaptive() {
        var binding = caseHub.getDefinition().getBindings().stream()
                .filter(b -> "watchdog-escalation".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.getWhen() instanceof JQExpressionEvaluator jq
                        && jq.expression().contains("deadlinePassed == true"),
                "watchdog-escalation should be adaptive — fires only when deadline passed");
    }

    @Test
    void quoteReceivedBindingChecksChannelMessage() {
        var binding = caseHub.getDefinition().getBindings().stream()
                .filter(b -> "quote-received".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.getWhen() instanceof JQExpressionEvaluator jq
                        && jq.expression().contains("channelMessage")
                        && jq.expression().contains("RESPONSE"),
                "quote-received binding should check .channelMessage.messageType == RESPONSE");
    }

    @Test
    void lifeCaseType() {
        assertEquals(LifeCaseType.CONTRACTOR_COORDINATION, caseHub.lifeCaseType());
    }
}
