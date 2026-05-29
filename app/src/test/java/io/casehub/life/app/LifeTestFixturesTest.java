package io.casehub.life.app;

import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class LifeTestFixturesTest {

    @Test
    @Transactional
    void seedStandardTemplates_createsThreeTemplates() {
        LifeTestFixtures.seedStandardTemplates();

        assertThat(WorkItemTemplate.find("name", "household-task").count()).isEqualTo(1);
        assertThat(WorkItemTemplate.find("name", "health-appointment").count()).isEqualTo(1);
        assertThat(WorkItemTemplate.find("name", "contractor-coordination").count()).isEqualTo(1);

        WorkItemTemplate t = WorkItemTemplate.find("name", "household-task").firstResult();
        assertThat(t.candidateGroups).isEqualTo("household-member");
        assertThat(t.priority).isEqualTo(WorkItemPriority.MEDIUM);
        assertThat(t.defaultExpiryHours).isEqualTo(24);
        assertThat(t.createdBy).isEqualTo("life-system");
    }

    @Test
    @Transactional
    void seedEscalationTemplate_createsHighPriorityAdminTemplate() {
        LifeTestFixtures.seedEscalationTemplate();

        WorkItemTemplate t = WorkItemTemplate.find("name", "life-escalation").firstResult();
        assertThat(t).isNotNull();
        assertThat(t.candidateGroups).isEqualTo("household-admin");
        assertThat(t.priority).isEqualTo(WorkItemPriority.HIGH);
        assertThat(t.description).isNotNull().isNotBlank();
    }

    @Test
    @Transactional
    void seedStandardTemplates_isIdempotent() {
        LifeTestFixtures.seedStandardTemplates();
        LifeTestFixtures.seedStandardTemplates();

        assertThat(WorkItemTemplate.find("name", "household-task").count()).isEqualTo(1);
        assertThat(WorkItemTemplate.find("name", "health-appointment").count()).isEqualTo(1);
        assertThat(WorkItemTemplate.find("name", "contractor-coordination").count()).isEqualTo(1);
    }

    @Test
    @Transactional
    void seedEscalationTemplate_isIdempotent() {
        LifeTestFixtures.seedEscalationTemplate();
        LifeTestFixtures.seedEscalationTemplate();

        assertThat(WorkItemTemplate.find("name", "life-escalation").count()).isEqualTo(1);
    }
}
