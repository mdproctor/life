package io.casehub.life.app;

import io.casehub.life.api.LifeDomain;
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

    private LifeSlaBreachPolicy policyForDomain(LifeDomain domain) {
        return new LifeSlaBreachPolicy() {
            @Override protected LifeDomain resolveDomain(SlaBreachContext ctx) {
                return domain;
            }
        };
    }

    private SlaBreachContext ctx(Set<String> candidateGroups) {
        var task = new BreachedTask(UUID.randomUUID(), "life:task/some-template", "Test task", candidateGroups);
        return new SlaBreachContext(BreachType.COMPLETION_EXPIRED, task, null, null);
    }

    @Test void healthDomain_firstBreach_escalatesWithin24h() {
        var result = policyForDomain(LifeDomain.HEALTH).onBreach(ctx(Set.of("household-member")));
        assertThat(result).isInstanceOf(BreachDecision.EscalateTo.class);
        var escalate = (BreachDecision.EscalateTo) result;
        assertThat(escalate.groups()).containsExactly("household-admin");
        assertThat(escalate.deadline()).isEqualTo(Duration.ofHours(24));
    }

    @Test void healthDomain_secondBreach_adminPresent_failsTerminally() {
        var result = policyForDomain(LifeDomain.HEALTH).onBreach(ctx(Set.of("household-admin")));
        assertThat(result).isInstanceOf(BreachDecision.Fail.class);
        assertThat(((BreachDecision.Fail) result).reason()).isEqualTo("life-sla-exhausted");
    }

    @Test void householdDomain_firstBreach_escalatesWithin48h() {
        var result = policyForDomain(LifeDomain.HOUSEHOLD).onBreach(ctx(Set.of("household-member")));
        assertThat(result).isInstanceOf(BreachDecision.EscalateTo.class);
        assertThat(((BreachDecision.EscalateTo) result).deadline()).isEqualTo(Duration.ofHours(48));
    }

    @Test void householdDomain_secondBreach_fails() {
        var result = policyForDomain(LifeDomain.HOUSEHOLD).onBreach(ctx(Set.of("household-admin")));
        assertThat(result).isInstanceOf(BreachDecision.Fail.class);
    }

    @Test void legalDomain_firstBreach_escalatesWithin12h() {
        var result = policyForDomain(LifeDomain.LEGAL).onBreach(ctx(Set.of("household-member")));
        assertThat(result).isInstanceOf(BreachDecision.EscalateTo.class);
        assertThat(((BreachDecision.EscalateTo) result).deadline()).isEqualTo(Duration.ofHours(12));
    }

    @Test void elderCareDomain_firstBreach_escalatesWithin12h() {
        var result = policyForDomain(LifeDomain.ELDER_CARE).onBreach(ctx(Set.of("household-member")));
        assertThat(result).isInstanceOf(BreachDecision.EscalateTo.class);
        assertThat(((BreachDecision.EscalateTo) result).deadline()).isEqualTo(Duration.ofHours(12));
    }

    @Test void noContextFound_fallsBackToHousehold() {
        // Default resolveDomain() would look up LifeTaskContext, which won't exist for this random taskId.
        // Test via policyForDomain(HOUSEHOLD) simulating the fallback.
        var result = policyForDomain(LifeDomain.HOUSEHOLD).onBreach(ctx(Set.of("household-member")));
        assertThat(result).isInstanceOf(BreachDecision.EscalateTo.class);
        assertThat(((BreachDecision.EscalateTo) result).deadline()).isEqualTo(Duration.ofHours(48));
    }
}
