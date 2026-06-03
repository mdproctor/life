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
package io.casehub.life.app;

import io.casehub.api.spi.routing.AgentRoutingStrategy;
import io.casehub.ledger.routing.TrustWeightedAgentStrategy;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link TrustWeightedAgentStrategy} has displaced
 * {@code LeastLoadedAgentStrategy} from casehub-engine.
 *
 * <p>The engine provides {@code LeastLoadedAgentStrategy @Priority(0)} as default.
 * casehub-engine-ledger provides {@code TrustWeightedAgentStrategy @Alternative @Priority(1)},
 * which has higher priority and becomes the active implementation.
 *
 * <p>This test verifies the CDI wiring is correct and the trust-based routing
 * strategy is active at startup.
 */
@QuarkusTest
class TrustStrategyDisplacementTest {

    @Inject
    AgentRoutingStrategy strategy;

    /**
     * Verifies that the injected {@link AgentRoutingStrategy} is an instance of
     * {@link TrustWeightedAgentStrategy}, not the default {@code LeastLoadedAgentStrategy}.
     */
    @Test
    void trustWeightedStrategyIsActive() {
        assertThat(strategy)
                .isInstanceOf(TrustWeightedAgentStrategy.class)
                .as("AgentRoutingStrategy should be TrustWeightedAgentStrategy (from casehub-engine-ledger)");
    }
}
