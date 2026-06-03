package io.casehub.life.app.routing;

import io.casehub.life.api.LifeTrustDimensions;
import io.casehub.platform.api.preferences.PreferenceKey;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LifeTrustRoutingPolicyKeys.
 * Verifies preference key structure and completeness.
 */
class LifeTrustRoutingPolicyKeysTest {

    @Test
    void allFloorKeysContainsAllDimensions() {
        Map<String, PreferenceKey<DoublePreference>> floorKeys = LifeTrustRoutingPolicyKeys.allFloorKeys();

        assertEquals(4, floorKeys.size(), "Should have exactly 4 floor keys");
        assertTrue(floorKeys.containsKey(LifeTrustDimensions.DEADLINE_RELIABILITY),
            "Should contain deadline-reliability");
        assertTrue(floorKeys.containsKey(LifeTrustDimensions.COST_ACCURACY),
            "Should contain cost-accuracy");
        assertTrue(floorKeys.containsKey(LifeTrustDimensions.FACTUAL_ACCURACY),
            "Should contain factual-accuracy");
        assertTrue(floorKeys.containsKey(LifeTrustDimensions.PROACTIVE_ALERTING),
            "Should contain proactive-alerting");
    }

    @Test
    void blendFactorKeyHasExpectedQualifiedName() {
        String qualifiedName = LifeTrustRoutingPolicyKeys.BLEND_FACTOR.qualifiedName();
        assertEquals("casehubio.life.trust-routing.blend-factor", qualifiedName,
            "Blend factor key should have expected qualified name");
    }

    @Test
    void floorKeysHaveCorrectQualifiedNames() {
        assertEquals("casehubio.life.trust-routing.floor.deadline-reliability",
            LifeTrustRoutingPolicyKeys.FLOOR_DEADLINE_RELIABILITY.qualifiedName());
        assertEquals("casehubio.life.trust-routing.floor.cost-accuracy",
            LifeTrustRoutingPolicyKeys.FLOOR_COST_ACCURACY.qualifiedName());
        assertEquals("casehubio.life.trust-routing.floor.factual-accuracy",
            LifeTrustRoutingPolicyKeys.FLOOR_FACTUAL_ACCURACY.qualifiedName());
        assertEquals("casehubio.life.trust-routing.floor.proactive-alerting",
            LifeTrustRoutingPolicyKeys.FLOOR_PROACTIVE_ALERTING.qualifiedName());
    }

    @Test
    void doublePreferenceParseAndValue() {
        DoublePreference pref = DoublePreference.parse("0.75");
        assertEquals(0.75, pref.value(), 0.0001);

        DoublePreference pref2 = DoublePreference.of(0.60);
        assertEquals(0.60, pref2.value(), 0.0001);
    }
}
