package io.casehub.life.api;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LifeCaseTypeTest {
    @Test
    void allSixTypesExist() {
        assertThat(LifeCaseType.values()).hasSize(6);
        assertThat(LifeCaseType.valueOf("TRAVEL_PLAN")).isNotNull();
        assertThat(LifeCaseType.valueOf("HOME_MAINTENANCE")).isNotNull();
        assertThat(LifeCaseType.valueOf("CARE_COORDINATION")).isNotNull();
        assertThat(LifeCaseType.valueOf("APPOINTMENT_CYCLE")).isNotNull();
        assertThat(LifeCaseType.valueOf("CONTRACTOR_COORDINATION")).isNotNull();
        assertThat(LifeCaseType.valueOf("FINANCIAL_REVIEW")).isNotNull();
    }

    @Test
    void caseNameMatchesYamlConvention() {
        assertThat(LifeCaseType.TRAVEL_PLAN.caseName()).isEqualTo("travel-plan");
        assertThat(LifeCaseType.HOME_MAINTENANCE.caseName()).isEqualTo("home-maintenance");
        assertThat(LifeCaseType.CARE_COORDINATION.caseName()).isEqualTo("care-coordination");
        assertThat(LifeCaseType.APPOINTMENT_CYCLE.caseName()).isEqualTo("appointment-cycle");
        assertThat(LifeCaseType.CONTRACTOR_COORDINATION.caseName()).isEqualTo("contractor-coordination");
        assertThat(LifeCaseType.FINANCIAL_REVIEW.caseName()).isEqualTo("financial-review");
    }

    @Test
    void eachCaseTypeMapsToDomain() {
        assertThat(LifeCaseType.TRAVEL_PLAN.domain()).isEqualTo(LifeDomain.TRAVEL);
        assertThat(LifeCaseType.HOME_MAINTENANCE.domain()).isEqualTo(LifeDomain.HOUSEHOLD);
        assertThat(LifeCaseType.CARE_COORDINATION.domain()).isEqualTo(LifeDomain.ELDER_CARE);
        assertThat(LifeCaseType.APPOINTMENT_CYCLE.domain()).isEqualTo(LifeDomain.HEALTH);
        assertThat(LifeCaseType.CONTRACTOR_COORDINATION.domain()).isEqualTo(LifeDomain.CONTRACTOR_COORDINATION);
        assertThat(LifeCaseType.FINANCIAL_REVIEW.domain()).isEqualTo(LifeDomain.FINANCE);
    }

    @Test
    void allCaseTypesHaveDomainMapping() {
        for (LifeCaseType type : LifeCaseType.values()) {
            assertThat(type.domain()).as(type + " must map to a LifeDomain").isNotNull();
        }
    }

}
