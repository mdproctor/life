package io.casehub.life.app.commitment;

import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.commitment.CommitmentOutcome;
import io.casehub.life.api.request.CommitmentRequest;
import io.casehub.life.app.LifeTestFixtures;
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
 * Tests DelegationCommitmentStrategy domain population.
 */
@QuarkusTest
class DelegationCommitmentStrategyTest {

    @Inject
    DelegationCommitmentStrategy strategy;

    @BeforeEach
    @Transactional
    void seedTemplates() {
        LifeTestFixtures.seedStandardTemplates();
    }

    @Test
    @Transactional
    void execute_setsDomainFromTaskContext() {
        final WorkItem workItem = createWorkItem("Delegation domain test");
        final LifeTaskContext taskContext = createTaskContext(workItem.id, LifeDomain.HOUSEHOLD);

        final CommitmentRequest request = new CommitmentRequest(
                "alice", null, Instant.now().plusSeconds(3600));
        final DelegationContext ctx = new DelegationContext(request, workItem, taskContext);

        final CommitmentOutcome outcome = strategy.execute(ctx);

        final LifeCommitmentRecord record = LifeCommitmentRecord
                .findByCorrelationId(outcome.correlationId())
                .orElseThrow();
        assertThat(record.domain).isEqualTo(LifeDomain.HOUSEHOLD);
        assertThat(record.delegateTo).isEqualTo("alice");
        assertThat(record.oversightKey).isNull();
    }

    @Test
    @Transactional
    void execute_setsDomainFromTaskContext_healthDomain() {
        final WorkItem workItem = createWorkItem("Health delegation test");
        final LifeTaskContext taskContext = createTaskContext(workItem.id, LifeDomain.HEALTH);

        final CommitmentRequest request = new CommitmentRequest(
                "bob", null, Instant.now().plusSeconds(3600));
        final DelegationContext ctx = new DelegationContext(request, workItem, taskContext);

        final CommitmentOutcome outcome = strategy.execute(ctx);

        final LifeCommitmentRecord record = LifeCommitmentRecord
                .findByCorrelationId(outcome.correlationId())
                .orElseThrow();
        assertThat(record.domain).isEqualTo(LifeDomain.HEALTH);
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
