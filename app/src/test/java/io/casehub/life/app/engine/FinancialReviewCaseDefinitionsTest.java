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
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Definition test for the financial-review fluent DSL.
 *
 * <p>Verifies that the programmatic CaseDefinition matches the YAML structure.
 * Plain JUnit — no Quarkus.
 */
class FinancialReviewCaseDefinitionsTest {

    @Test
    void definitionBuilds() {
        var def = FinancialReviewCaseDefinitions.financialReview();
        assertNotNull(def);
        assertEquals("casehub-life", def.getNamespace());
        assertEquals("financial-review", def.getName());
        assertEquals("1.0.0", def.getVersion());
    }

    @Test
    void hasFiveCapabilities() {
        var def = FinancialReviewCaseDefinitions.financialReview();
        var names = def.getCapabilities().stream().map(c -> c.name()).toList();
        assertEquals(5, names.size());
        assertTrue(names.containsAll(List.of(
                "gather-data", "analyse-anomalies", "escalate-anomalies",
                "oversight-response", "produce-report")));
    }

    @Test
    void hasFiveBindings() {
        var def = FinancialReviewCaseDefinitions.financialReview();
        var names = def.getBindings().stream().map(b -> b.getName()).toList();
        assertEquals(5, names.size());
        assertTrue(names.containsAll(List.of(
                "gather-data", "analyse-anomalies", "escalate-anomalies",
                "oversight-response", "produce-report")));
    }

    @Test
    void hasReviewCompleteGoal() {
        var def = FinancialReviewCaseDefinitions.financialReview();
        var goals = def.getGoals();
        assertEquals(1, goals.size());
        assertEquals("review-complete", goals.get(0).getName());
        assertTrue(goals.get(0).getCondition() instanceof JQExpressionEvaluator jq
                        && jq.expression().contains("report"),
                "Goal condition should check report");
    }

    @Test
    void escalateAnomaliesIsAdaptive() {
        var def = FinancialReviewCaseDefinitions.financialReview();
        var binding = def.getBindings().stream()
                .filter(b -> "escalate-anomalies".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.getWhen() instanceof JQExpressionEvaluator jq
                        && jq.expression().contains("hasAnomalies == true"),
                "escalate-anomalies should be adaptive — fires only when anomalies detected");
    }

    @Test
    void oversightResponseBindingChecksChannelMessage() {
        var def = FinancialReviewCaseDefinitions.financialReview();
        var binding = def.getBindings().stream()
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
        var def = FinancialReviewCaseDefinitions.financialReview();
        var binding = def.getBindings().stream()
                .filter(b -> "produce-report".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.getWhen() instanceof JQExpressionEvaluator jq
                        && jq.expression().contains("hasAnomalies != true")
                        && jq.expression().contains("oversightDecision != null"),
                "produce-report binding should handle both paths: no anomalies OR oversight decision received");
    }
}
