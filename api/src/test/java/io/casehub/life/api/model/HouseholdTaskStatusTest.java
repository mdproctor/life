package io.casehub.life.api.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HouseholdTaskStatusTest {

    @Test
    void allExpectedStatusesPresent() {
        assertThat(HouseholdTaskStatus.values()).extracting(Enum::name).containsExactlyInAnyOrder(
                "PENDING",
                "IN_PROGRESS",
                "COMPLETED",
                "CANCELLED"
        );
    }
}
