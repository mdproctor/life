package io.casehub.life.app.commitment;

import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.commitment.CommitmentOutcome;
import io.casehub.life.api.request.CreateLifeTaskRequest;
import io.casehub.life.api.request.OversightGateRequest;
import io.casehub.life.app.entity.LifeCommitmentRecord;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests OversightGateStrategy domain and oversightKey population.
 *
 * Runs as @QuarkusTest to get full CDI wiring (MessageService, ChannelInitializer,
 * DomainLedgerHandlers). Tests verify record state after execute().
 */
@QuarkusTest
class OversightGateStrategyTest {

    @Inject
    OversightGateStrategy strategy;

    @Test
    @Transactional
    void execute_setsDomainFromRequest() {
        final OversightGateRequest request = new OversightGateRequest(
                LifeDomain.FINANCE,
                Instant.now().plusSeconds(3600),
                new CreateLifeTaskRequest("household-task", "Buy new car", null, null),
                BigDecimal.valueOf(5000),
                "vehicle"
        );

        final CommitmentOutcome outcome = strategy.execute(new OversightContext(request));

        final LifeCommitmentRecord record = LifeCommitmentRecord
                .findByCorrelationId(outcome.correlationId())
                .orElseThrow();
        assertThat(record.domain).isEqualTo(LifeDomain.FINANCE);
    }

    @Test
    @Transactional
    void execute_setsDomainFromRequest_health() {
        final OversightGateRequest request = new OversightGateRequest(
                LifeDomain.HEALTH,
                Instant.now().plusSeconds(3600),
                new CreateLifeTaskRequest("household-task", "Expensive treatment", null, null),
                BigDecimal.valueOf(2000),
                "medical"
        );

        final CommitmentOutcome outcome = strategy.execute(new OversightContext(request));

        final LifeCommitmentRecord record = LifeCommitmentRecord
                .findByCorrelationId(outcome.correlationId())
                .orElseThrow();
        assertThat(record.domain).isEqualTo(LifeDomain.HEALTH);
    }

    @Test
    @Transactional
    void execute_setsOversightKeyAndDelegateToNull() {
        final OversightGateRequest request = new OversightGateRequest(
                LifeDomain.FINANCE,
                Instant.now().plusSeconds(3600),
                new CreateLifeTaskRequest("household-task", "Buy new motorbike", null, null),
                BigDecimal.valueOf(8000),
                "vehicle"
        );

        final CommitmentOutcome outcome = strategy.execute(new OversightContext(request));

        final LifeCommitmentRecord record = LifeCommitmentRecord
                .findByCorrelationId(outcome.correlationId())
                .orElseThrow();
        assertThat(record.oversightKey).isEqualTo("Buy new motorbike:household-task");
        assertThat(record.delegateTo).isNull();
    }

    @Test
    @Transactional
    void execute_dedupUsesOversightKey() {
        final OversightGateRequest request = new OversightGateRequest(
                LifeDomain.FINANCE,
                Instant.now().plusSeconds(3600),
                new CreateLifeTaskRequest("household-task", "Duplicate gate", null, null),
                BigDecimal.valueOf(1000),
                "general"
        );

        // First call succeeds
        strategy.execute(new OversightContext(request));

        // Second call with same title+templateRef should throw CommitmentConflictException
        try {
            strategy.execute(new OversightContext(request));
            assertThat(true).as("Expected CommitmentConflictException").isFalse();
        } catch (CommitmentConflictException e) {
            assertThat(e.getMessage()).contains("Duplicate gate");
        }
    }
}
