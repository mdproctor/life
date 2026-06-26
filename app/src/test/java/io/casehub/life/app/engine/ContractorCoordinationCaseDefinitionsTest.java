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
import io.casehub.api.model.evaluator.ListEvaluator;

/**
 * DSL test for the contractor-coordination case definition.
 *
 * <p>Verifies that the fluent DSL companion produces a structurally equivalent
 * definition to the YAML: same name, namespace, bindings, goals, capabilities,
 * humanTask targets, adaptive watchdog condition, and channelMessage binding.
 */
class ContractorCoordinationCaseDefinitionsTest {

    private final CaseDefinition def = ContractorCoordinationCaseDefinitions.contractorCoordination();

    @Test
    void definitionMetadata() {
        assertNotNull(def);
        assertEquals("casehub-life", def.getNamespace());
        assertEquals("contractor-coordination", def.getName());
        assertEquals("1.0.0", def.getVersion());
    }

    @Test
    void hasFiveCapabilities() {
        var names = def.getCapabilities()
                .stream().map(c -> c.name()).toList();
        assertEquals(5, names.size());
        assertTrue(names.containsAll(List.of(
                "request-quote", "watchdog-escalation", "quote-received",
                "job-monitoring", "record-payment")));
    }

    @Test
    void hasSevenBindings() {
        var names = def.getBindings()
                .stream().map(b -> b.getName()).toList();
        assertEquals(7, names.size());
        assertTrue(names.containsAll(List.of(
                "request-quote", "watchdog-escalation", "quote-received",
                "approve-quote", "job-monitoring", "payment-gate",
                "record-payment")));
    }

    @Test
    void approveQuoteIsHumanTask() {
        var binding = def.getBindings().stream()
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
        var binding = def.getBindings().stream()
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
        var goals = def.getGoals();
        assertEquals(1, goals.size());
        assertEquals("contractor-paid", goals.get(0).getName());
        assertTrue(goals.get(0).getCondition() instanceof JQExpressionEvaluator jq
                        && jq.expression().contains("paymentRecorded"),
                "Goal condition should check paymentRecorded");
    }

    @Test
    void watchdogEscalationIsAdaptive() {
        var binding = def.getBindings().stream()
                .filter(b -> "watchdog-escalation".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.getWhen() instanceof JQExpressionEvaluator jq
                        && jq.expression().contains("deadlinePassed == true"),
                "watchdog-escalation should be adaptive — fires only when deadline passed");
    }

    @Test
    void quoteReceivedBindingChecksChannelMessage() {
        var binding = def.getBindings().stream()
                .filter(b -> "quote-received".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.getWhen() instanceof JQExpressionEvaluator jq
                        && jq.expression().contains("channelMessage")
                        && jq.expression().contains("RESPONSE"),
                "quote-received binding should check .channelMessage.messageType == RESPONSE");
    }
}
