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

public final class ContractorCoordinationDomainDescriptor implements LifeDomainDescriptor {
    @Override public String capability()       { return LifeCapabilities.CONTRACTOR_COORDINATION; }
    @Override public String templateCategory() { return "contractor"; }

    @Override public Set<String> workerCapabilities() {
        return Set.of("request-quote", "watchdog-escalation", "quote-received",
                      "job-monitoring", "record-payment");
    }

    @Override public LifeRoutingPolicy routingPolicy() {
        return new LifeRoutingPolicy(OptionalDouble.of(0.65), OptionalInt.of(8),
                OptionalDouble.of(0.05), Optional.of("household-admin"),
                "Contractor follow-up balances deadline reliability and cost accuracy");
    }

    @Override public LifeSlaPolicy slaPolicy() {
        return new LifeSlaPolicy("household-admin", Duration.ofHours(48));
    }
}
