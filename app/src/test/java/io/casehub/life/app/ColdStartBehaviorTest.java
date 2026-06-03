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

import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.ledger.runtime.service.TrustGateService;
import io.casehub.life.api.LifeActorIds;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies graceful behavior when no trust data exists (cold start scenario).
 *
 * <p>On first deployment or after data wipe, the trust routing layer should:
 * <ul>
 *   <li>Return routing policies even with no historical trust data (code-driven, not data-driven)</li>
 *   <li>Return empty trust scores for unknown actors</li>
 *   <li>Return default routing policy for unknown capabilities</li>
 * </ul>
 *
 * <p>This ensures the system boots and routes work even before any trust attestations
 * have been recorded.
 */
@QuarkusTest
class ColdStartBehaviorTest {

    @Inject
    TrustRoutingPolicyProvider policyProvider;

    @Inject
    TrustGateService trustGateService;

    /**
     * Verifies that routing policies are available from the provider even with no trust data.
     * The policy threshold (0.75 by default) is code-driven, not loaded from a database.
     */
    @Test
    void policiesAvailableWithNoTrustData() {
        var policy = policyProvider.forCapability("book-appointment");
        assertThat(policy)
                .isNotNull()
                .as("TrustRoutingPolicy should be available for known capability");
        assertThat(policy.threshold())
                .isEqualTo(0.75)
                .as("Policy threshold should match configured default");
    }

    /**
     * Verifies that TrustGateService is available for injection.
     * This ensures the trust scoring layer is wired and available at startup.
     */
    @Test
    void trustGateServiceIsAvailable() {
        assertThat(trustGateService)
                .isNotNull()
                .as("TrustGateService should be available for injection");
    }

    /**
     * Verifies that unknown capabilities return the default routing policy.
     * This ensures graceful fallback for capabilities not explicitly configured.
     */
    @Test
    void unknownCapabilityReturnsDefaultPolicy() {
        var policy = policyProvider.forCapability("nonexistent-capability");
        assertThat(policy)
                .isEqualTo(TrustRoutingPolicy.DEFAULT)
                .as("Unknown capability should return DEFAULT policy");
    }
}
