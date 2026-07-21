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

import io.casehub.api.model.Binding;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.Goal;
import io.casehub.api.model.GoalKind;
import io.casehub.api.model.evaluator.JQExpressionEvaluator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the DSL-built family-vote definition matches the YAML structure.
 */
class FamilyVoteCaseDefinitionsTest {

    @Test
    void shouldBuildFamilyVoteDefinition() {
        CaseDefinition def = FamilyVoteCaseDefinitions.familyVote();

        assertThat(def.getName()).isEqualTo("family-vote");
        assertThat(def.getNamespace()).isEqualTo("life");
        assertThat(def.getVersion()).isEqualTo("1.0.0");
        assertThat(def.getTitle()).isEqualTo("Family vote — single humanTask child case for M-of-N quorum");
    }

    @Test
    void shouldHaveVoteCastGoal() {
        CaseDefinition def = FamilyVoteCaseDefinitions.familyVote();

        assertThat(def.getGoals()).hasSize(1);
        Goal voteCast = def.getGoals().iterator().next();
        assertThat(voteCast.getName()).isEqualTo("vote-cast");
        assertThat(voteCast.getKind()).isEqualTo(GoalKind.SUCCESS.value());
        assertTrue(voteCast.getCondition() instanceof JQExpressionEvaluator jq
                        && jq.expression().contains(".vote != null"),
                "Goal condition should check .vote != null");
    }

    @Test
    void shouldHaveCastVoteBinding() {
        CaseDefinition def = FamilyVoteCaseDefinitions.familyVote();

        assertThat(def.getBindings()).hasSize(1);
        Binding castVote = def.getBindings().iterator().next();
        assertThat(castVote.getName()).isEqualTo("cast-vote");
        assertThat(castVote.getWhen().toString()).contains(".vote == null");
        assertThat(castVote.target()).isNotNull();
    }

    @Test
    void shouldHaveHumanTaskTarget() {
        CaseDefinition def = FamilyVoteCaseDefinitions.familyVote();

        Binding castVote = def.getBindings().iterator().next();
        assertThat(castVote.target().getClass().getSimpleName()).isEqualTo("HumanTaskTarget");
    }

    @Test
    void shouldMatchYamlStructure() {
        CaseDefinition dslDef = FamilyVoteCaseDefinitions.familyVote();

        // Verify goal
        Goal voteCast = dslDef.getGoals().iterator().next();
        assertThat(voteCast.getName()).isEqualTo("vote-cast");
        assertTrue(voteCast.getCondition() instanceof JQExpressionEvaluator jq
                        && jq.expression().contains(".vote != null"),
                "Goal condition should check .vote != null");

        // Verify binding
        Binding castVote = dslDef.getBindings().iterator().next();
        assertThat(castVote.getName()).isEqualTo("cast-vote");
        assertThat(castVote.getWhen().toString()).contains(".vote == null");
    }
}
