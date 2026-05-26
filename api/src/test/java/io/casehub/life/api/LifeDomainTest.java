package io.casehub.life.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LifeDomainTest {

    @Test
    void allExpectedDomainsPresent() {
        assertThat(LifeDomain.values()).extracting(Enum::name).containsExactlyInAnyOrder(
                "HOUSEHOLD",
                "HEALTH",
                "FINANCE",
                "FAMILY_SCHEDULING",
                "TRAVEL",
                "LEGAL",
                "CONTRACTOR_COORDINATION",
                "ELDER_CARE"
        );
    }
}
