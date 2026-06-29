package io.casehub.life.app.commitment;

import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.request.CommitmentRequest;
import io.casehub.life.api.request.CreateLifeTaskRequest;
import io.casehub.life.api.request.OversightGateRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LifeCommitmentStrategyTest {

    final DelegationCommitmentStrategy delegation = new DelegationCommitmentStrategy();
    final ContractorCommitmentStrategy contractor = new ContractorCommitmentStrategy();
    final OversightGateStrategy oversight = new OversightGateStrategy();

    @Test
    void delegationContext_onlyDelegationApplies() {
        final var ctx = new DelegationContext(
                new CommitmentRequest("alice", null, Instant.now().plusSeconds(3600)),
                null, null);
        assertThat(delegation.applies(ctx)).isTrue();
        assertThat(contractor.applies(ctx)).isFalse();
        assertThat(oversight.applies(ctx)).isFalse();
    }

    @Test
    void contractorContext_onlyContractorApplies() {
        final var ctx = new ContractorContext(
                new CommitmentRequest(null, UUID.randomUUID(), Instant.now().plusSeconds(3600)),
                null, null, null);
        assertThat(delegation.applies(ctx)).isFalse();
        assertThat(contractor.applies(ctx)).isTrue();
        assertThat(oversight.applies(ctx)).isFalse();
    }

    @Test
    void oversightContext_onlyOversightApplies() {
        final var ctx = new OversightContext(
                new OversightGateRequest(LifeDomain.FINANCE, Instant.now().plusSeconds(3600),
                        new CreateLifeTaskRequest("household-task", "Buy groceries", null, null), null, null));
        assertThat(delegation.applies(ctx)).isFalse();
        assertThat(contractor.applies(ctx)).isFalse();
        assertThat(oversight.applies(ctx)).isTrue();
    }

    @Test
    void delegation_withoutDeadline_doesNotApply() {
        final var ctx = new DelegationContext(
                new CommitmentRequest("alice", null, null),
                null, null);
        assertThat(delegation.applies(ctx)).isFalse();
    }

    @Test
    void delegation_withoutDelegateTo_doesNotApply() {
        final var ctx = new DelegationContext(
                new CommitmentRequest(null, null, Instant.now().plusSeconds(3600)),
                null, null);
        assertThat(delegation.applies(ctx)).isFalse();
    }

    @Test
    void contractor_withoutDeadline_doesNotApply() {
        final var ctx = new ContractorContext(
                new CommitmentRequest(null, UUID.randomUUID(), null),
                null, null, null);
        assertThat(contractor.applies(ctx)).isFalse();
    }

    @Test
    void contextTypes_areExclusive_noOverlap() {
        // Delegation: delegateTo set + deadline set
        final var delegCtx = new DelegationContext(
                new CommitmentRequest("bob", null, Instant.now().plusSeconds(3600)),
                null, null);
        // Contractor: externalActorId set + deadline set
        final var contractCtx = new ContractorContext(
                new CommitmentRequest(null, UUID.randomUUID(), Instant.now().plusSeconds(3600)),
                null, null, null);
        // Oversight
        final var oversightCtx = new OversightContext(
                new OversightGateRequest(LifeDomain.FINANCE, Instant.now().plusSeconds(3600),
                        new CreateLifeTaskRequest("household-task", "Title", null, null), null, null));

        // Each strategy applies to exactly one context type
        assertThat(delegation.applies(delegCtx) ? 1 : 0)
                .as("Delegation applies to delegation context only")
                .isEqualTo(1);
        assertThat(contractor.applies(contractCtx) ? 1 : 0)
                .as("Contractor applies to contractor context only")
                .isEqualTo(1);
        assertThat(oversight.applies(oversightCtx) ? 1 : 0)
                .as("Oversight applies to oversight context only")
                .isEqualTo(1);
    }
}
