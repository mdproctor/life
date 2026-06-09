package io.casehub.life.app.spi;

import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.LifeSlaPolicy;
import io.casehub.work.api.BreachDecision;
import io.casehub.work.api.SlaBreachContext;
import io.casehub.work.api.SlaBreachPolicy;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LifeSlaBreachPolicy implements SlaBreachPolicy {

    private static final String CALLER_REF_PREFIX = "life:task/";

    @Override
    public BreachDecision onBreach(final SlaBreachContext ctx) {
        String callerRef = ctx.task().callerRef();
        String category = callerRef != null && callerRef.startsWith(CALLER_REF_PREFIX)
                ? callerRef.substring(CALLER_REF_PREFIX.length())
                : null;
        LifeDomain domain = LifeDomain.fromCategory(category).orElse(LifeDomain.HOUSEHOLD);
        LifeSlaPolicy policy = domain.descriptor().slaPolicy();

        // Tier detection: if escalation group is already in candidateGroups, tier 2 is exhausted.
        if (ctx.task().candidateGroups().contains(policy.escalationGroup())) {
            return new BreachDecision.Fail("life-sla-exhausted");
        }
        return BreachDecision.EscalateTo.to(policy.escalationGroup())
                .withDeadline(policy.escalationDeadline());
    }
}
