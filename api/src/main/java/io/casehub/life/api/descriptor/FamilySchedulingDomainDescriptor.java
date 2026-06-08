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

public final class FamilySchedulingDomainDescriptor implements LifeDomainDescriptor {
    @Override public String capability()       { return LifeCapabilities.FAMILY_SCHEDULING; }
    @Override public String templateCategory() { return "family"; }
    @Override public Set<String> workerCapabilities() { return Set.of(); }

    @Override public LifeRoutingPolicy routingPolicy() {
        return new LifeRoutingPolicy(OptionalDouble.of(0.50), OptionalInt.of(5),
                OptionalDouble.empty(), Optional.empty(),
                "Family calendar coordination is low-stakes");
    }

    @Override public LifeSlaPolicy slaPolicy() {
        return new LifeSlaPolicy("household-admin", Duration.ofHours(48));
    }
}
