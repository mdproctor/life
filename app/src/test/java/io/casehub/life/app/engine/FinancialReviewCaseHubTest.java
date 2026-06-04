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

import io.casehub.api.model.evaluator.JQExpressionEvaluator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/**
 * Definition test for the financial-review CaseHub.
 *
 * <p>Verifies that the YAML loads correctly and the augmented workers are present.
 */
@QuarkusTest
class FinancialReviewCaseHubTest {

    @Inject
    FinancialReviewCaseHub caseHub;

    @Test
    void definitionLoads() {
        var def = caseHub.getDefinition();
        assertNotNull(def);
        assertEquals("casehub-life", def.getNamespace());
        assertEquals("financial-review", def.getName());
        assertEquals("1.0.0", def.getVersion());
    }

    @Test
    void hasFiveCapabilities() {
        var names = caseHub.getDefinition().getCapabilities()
                .stream().map(c -> c.getName()).toList();
        assertEquals(5, names.size());
        assertTrue(names.containsAll(List.of(
                "gather-data", "analyse-anomalies", "escalate-anomalies",
                "oversight-response", "produce-report")));
    }

    @Test
    void hasFiveBindings() {
        var names = caseHub.getDefinition().getBindings()
                .stream().map(b -> b.getName()).toList();
        assertEquals(5, names.size());
        assertTrue(names.containsAll(List.of(
                "gather-data", "analyse-anomalies", "escalate-anomalies",
                "oversight-response", "produce-report")));
    }

    @Test
    void hasReviewCompleteGoal() {
        var goals = caseHub.getDefinition().getGoals();
        assertEquals(1, goals.size());
        assertEquals("review-complete", goals.get(0).getName());
        assertTrue(goals.get(0).getCondition() instanceof JQExpressionEvaluator jq
                        && jq.expression().contains("report"),
                "Goal condition should check report");
    }

    @Test
    void hasFiveWorkers() {
        var workers = caseHub.getDefinition().getWorkers();
        assertEquals(5, workers.size(), "Exactly 5 workers expected — size catches double-augmentation");
        var names = Set.copyOf(workers.stream().map(w -> w.getName()).toList());
        assertEquals(Set.of(
                "gather-data-agent", "analyse-anomalies-agent",
                "escalate-anomalies-agent", "oversight-response-agent",
                "produce-report-agent"), names);
    }

    @Test
    void escalateAnomaliesIsAdaptive() {
        var binding = caseHub.getDefinition().getBindings().stream()
                .filter(b -> "escalate-anomalies".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.getWhen() instanceof JQExpressionEvaluator jq
                        && jq.expression().contains("hasAnomalies == true"),
                "escalate-anomalies should be adaptive — fires only when anomalies detected");
    }

    @Test
    void oversightResponseBindingChecksChannelMessage() {
        var binding = caseHub.getDefinition().getBindings().stream()
                .filter(b -> "oversight-response".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.getWhen() instanceof JQExpressionEvaluator jq
                        && jq.expression().contains("channelMessage")
                        && jq.expression().contains("RESPONSE"),
                "oversight-response binding should check .channelMessage.messageType == RESPONSE");
    }

    @Test
    void produceReportBindingHandlesBothPaths() {
        var binding = caseHub.getDefinition().getBindings().stream()
                .filter(b -> "produce-report".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.getWhen() instanceof JQExpressionEvaluator jq
                        && jq.expression().contains("hasAnomalies != true")
                        && jq.expression().contains("oversightDecision != null"),
                "produce-report binding should handle both paths: no anomalies OR oversight decision received");
    }
}
