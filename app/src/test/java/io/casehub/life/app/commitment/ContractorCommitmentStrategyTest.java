package io.casehub.life.app.commitment;

import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.commitment.CommitmentOutcome;
import io.casehub.life.api.request.CommitmentRequest;
import io.casehub.life.app.LifeTestFixtures;
import io.casehub.life.app.entity.ExternalActor;
import io.casehub.life.app.entity.LifeCommitmentRecord;
import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.work.runtime.model.WorkItem;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests ContractorCommitmentStrategy domain population.
 */
@QuarkusTest
class ContractorCommitmentStrategyTest {

    @Inject
    ContractorCommitmentStrategy strategy;

    @BeforeEach
    @Transactional
    void seedTemplates() {
        LifeTestFixtures.seedStandardTemplates();
    }

    @Test
    @Transactional
    void execute_setsDomainFromTaskContext() {
        final ExternalActor actor = createExternalActor("Test Contractor");
        final WorkItem workItem = createWorkItem("Contractor domain test");
        final LifeTaskContext taskContext = createTaskContext(workItem.id, LifeDomain.CONTRACTOR_COORDINATION);

        final CommitmentRequest request = new CommitmentRequest(
                null, actor.id, Instant.now().plusSeconds(3600));
        final ContractorContext ctx = new ContractorContext(request, workItem, taskContext, actor);

        final CommitmentOutcome outcome = strategy.execute(ctx);

        final LifeCommitmentRecord record = LifeCommitmentRecord
                .findByCorrelationId(outcome.correlationId())
                .orElseThrow();
        assertThat(record.domain).isEqualTo(LifeDomain.CONTRACTOR_COORDINATION);
        assertThat(record.oversightKey).isNull();
    }

    @Test
    @Transactional
    void execute_setsDomainFromTaskContext_householdDomain() {
        final ExternalActor actor = createExternalActor("Plumber");
        final WorkItem workItem = createWorkItem("Household contractor test");
        final LifeTaskContext taskContext = createTaskContext(workItem.id, LifeDomain.HOUSEHOLD);

        final CommitmentRequest request = new CommitmentRequest(
                null, actor.id, Instant.now().plusSeconds(3600));
        final ContractorContext ctx = new ContractorContext(request, workItem, taskContext, actor);

        final CommitmentOutcome outcome = strategy.execute(ctx);

        final LifeCommitmentRecord record = LifeCommitmentRecord
                .findByCorrelationId(outcome.correlationId())
                .orElseThrow();
        assertThat(record.domain).isEqualTo(LifeDomain.HOUSEHOLD);
    }

    private ExternalActor createExternalActor(final String name) {
        final ExternalActor actor = new ExternalActor();
        actor.id = UUID.randomUUID();
        actor.name = name;
        actor.actorType = io.casehub.life.api.LifeActorType.EXTERNAL_HUMAN;
        actor.contactMethod = "EMAIL";
        actor.contactValue = name.toLowerCase().replace(" ", "") + "@test.com";
        actor.persist();
        return actor;
    }

    private WorkItem createWorkItem(final String title) {
        final WorkItem w = new WorkItem();
        w.id = UUID.randomUUID();
        w.title = title;
        w.status = io.casehub.work.api.WorkItemStatus.PENDING;
        w.createdAt = Instant.now();
        w.updatedAt = Instant.now();
        w.tenancyId = "278776f9-e1b0-46fb-9032-8bddebdcf9ce";
        w.persist();
        return w;
    }

    private LifeTaskContext createTaskContext(final UUID workItemId, final LifeDomain domain) {
        final LifeTaskContext ctx = new LifeTaskContext();
        ctx.workItemId = workItemId;
        ctx.domain = domain;
        ctx.persist();
        return ctx;
    }
}
