package io.casehub.life.api.descriptor;

import io.casehub.life.api.LifeCapabilities;
import io.casehub.life.api.LifeDomainDescriptor;
import io.casehub.life.api.LifeRoutingPolicy;
import io.casehub.life.api.LifeSlaPolicy;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

public final class HouseholdDomainDescriptor implements LifeDomainDescriptor {
    @Override public String capability()       { return LifeCapabilities.HOUSEHOLD_MANAGEMENT; }
    @Override public String templateCategory() { return "household"; }

    @Override public Set<String> workerCapabilities() {
        return Set.of("schedule-inspection", "get-quotes", "issue-commitment",
                      "monitor-job", "record-completion");
    }

    @Override public LifeRoutingPolicy routingPolicy() {
        return new LifeRoutingPolicy(OptionalDouble.of(0.50), OptionalInt.of(5),
                OptionalDouble.empty(), Optional.empty(),
                "Routine household tasks tolerate lower threshold, no escalation");
    }

    @Override public LifeSlaPolicy slaPolicy() {
        return new LifeSlaPolicy("household-admin", Duration.ofHours(48));
    }
}
