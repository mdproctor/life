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

import io.casehub.life.api.LifeCaseType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/**
 * Verifies the contract that LifeCaseService.resolve() depends on: every LifeCaseType has exactly
 * one matching LifeTypedCaseHub bean discoverable via CDI Instance.
 */
@QuarkusTest
class LifeCaseHubInstanceResolutionTest {

    @Inject @Any Instance<LifeTypedCaseHub> caseHubs;

    @Test
    void allLifeCaseTypesHaveExactlyOneMatchingCaseHub() {
        // Given all LifeCaseType enum values
        var allTypes = LifeCaseType.values();

        // When we discover all LifeTypedCaseHub beans
        var discoveredTypes =
                StreamSupport.stream(caseHubs.spliterator(), false)
                        .map(LifeTypedCaseHub::lifeCaseType)
                        .toList();

        // Then every LifeCaseType has exactly one matching CaseHub
        assertThat(discoveredTypes).doesNotHaveDuplicates();
        assertThat(discoveredTypes).containsExactlyInAnyOrder(allTypes);
    }

    @Test
    void instanceLookupFindsCorrectCaseHub() {
        // Given a specific LifeCaseType
        var type = LifeCaseType.APPOINTMENT_CYCLE;

        // When we look it up via Instance
        var hub =
                StreamSupport.stream(caseHubs.spliterator(), false)
                        .filter(h -> h.lifeCaseType() == type)
                        .findFirst();

        // Then we find exactly one matching hub
        assertThat(hub).isPresent();
        assertThat(hub.get().lifeCaseType()).isEqualTo(type);
    }
}
