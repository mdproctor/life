package io.casehub.life.app.spi;

import io.casehub.life.api.HouseholdGroups;
import io.casehub.life.api.LifeCaseStatus;
import io.casehub.life.api.LifeCaseType;
import io.casehub.life.api.response.LifeCaseResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JuniorLifeCaseVisibilityPolicyTest {

    private final JuniorLifeCaseVisibilityPolicy policy = new JuniorLifeCaseVisibilityPolicy();

    private LifeCaseResponse aCase() {
        return new LifeCaseResponse(UUID.randomUUID(), LifeCaseType.TRAVEL_PLAN,
                LifeCaseType.TRAVEL_PLAN.domain(), LifeCaseStatus.ACTIVE,
                Instant.now(), null);
    }

    @Test
    void adminAlwaysVisible() {
        assertThat(policy.isVisible(aCase(), "admin-1", Set.of(HouseholdGroups.ADMIN))).isTrue();
    }

    @Test
    void memberAlwaysVisible() {
        assertThat(policy.isVisible(aCase(), "member-1", Set.of(HouseholdGroups.MEMBER))).isTrue();
    }

    @Test
    void juniorNotVisibleAtPolicyLevel() {
        assertThat(policy.isVisible(aCase(), "junior-1", Set.of(HouseholdGroups.JUNIOR))).isFalse();
    }

    @Test
    void emptyGroupsNotVisible() {
        assertThat(policy.isVisible(aCase(), "unknown", Set.of())).isFalse();
    }
}
