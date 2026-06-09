package io.casehub.life.app;

import io.casehub.life.app.spi.LifeSlaBreachPolicy;
import io.casehub.work.api.BreachDecision;
import io.casehub.work.api.BreachType;
import io.casehub.work.api.BreachedTask;
import io.casehub.work.api.SlaBreachContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LifeSlaBreachPolicyTest {

    private final LifeSlaBreachPolicy policy = new LifeSlaBreachPolicy();

    private SlaBreachContext ctx(String callerRef, Set<String> candidateGroups) {
        var task = new BreachedTask(UUID.randomUUID(), callerRef, "Test task", candidateGroups);
        // policy does not use scope or preferences — null is safe
        return new SlaBreachContext(BreachType.COMPLETION_EXPIRED, task, null, null);
    }

    // ── health domain (24h escalation) ──────────────────────────────────────

    @Test
    void healthDomain_firstBreach_escalatesWithin24h() {
        var result = policy.onBreach(ctx("life:task/health", Set.of("household-member")));

        assertThat(result).isInstanceOf(BreachDecision.EscalateTo.class);
        var escalate = (BreachDecision.EscalateTo) result;
        assertThat(escalate.groups()).containsExactly("household-admin");
        assertThat(escalate.deadline()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void healthDomain_secondBreach_adminPresent_failsTerminally() {
        var result = policy.onBreach(ctx("life:task/health", Set.of("household-admin")));

        assertThat(result).isInstanceOf(BreachDecision.Fail.class);
        assertThat(((BreachDecision.Fail) result).reason()).isEqualTo("life-sla-exhausted");
    }

    // ── household domain (48h escalation) ───────────────────────────────────

    @Test
    void householdDomain_firstBreach_escalatesWithin48h() {
        var result = policy.onBreach(ctx("life:task/household", Set.of("household-member")));

        assertThat(result).isInstanceOf(BreachDecision.EscalateTo.class);
        assertThat(((BreachDecision.EscalateTo) result).deadline()).isEqualTo(Duration.ofHours(48));
    }

    @Test
    void householdDomain_secondBreach_adminAndOtherGroups_failsTerminally() {
        var result = policy.onBreach(ctx("life:task/household", Set.of("household-admin", "household-member")));

        assertThat(result).isInstanceOf(BreachDecision.Fail.class);
    }

    // ── fallback / unknown domain (defaults to HOUSEHOLD 48h) ───────────────

    @Test
    void unknownCallerRef_fallsBackToHousehold48h() {
        var result = policy.onBreach(ctx("life:task/unknown-category", Set.of("household-member")));

        assertThat(result).isInstanceOf(BreachDecision.EscalateTo.class);
        assertThat(((BreachDecision.EscalateTo) result).deadline()).isEqualTo(Duration.ofHours(48));
    }

    @Test
    void nullCallerRef_fallsBackToHousehold48h() {
        var result = policy.onBreach(ctx(null, Set.of("household-member")));

        assertThat(result).isInstanceOf(BreachDecision.EscalateTo.class);
        assertThat(((BreachDecision.EscalateTo) result).deadline()).isEqualTo(Duration.ofHours(48));
    }

    @Test
    void noCallerRefPrefix_fallsBackToHousehold48h() {
        var result = policy.onBreach(ctx("other:task/health", Set.of("household-member")));

        assertThat(result).isInstanceOf(BreachDecision.EscalateTo.class);
        assertThat(((BreachDecision.EscalateTo) result).deadline()).isEqualTo(Duration.ofHours(48));
    }

    @Test
    void firstBreach_emptyGroups_escalates() {
        var result = policy.onBreach(ctx("life:task/household", Set.of()));

        assertThat(result).isInstanceOf(BreachDecision.EscalateTo.class);
    }
}
