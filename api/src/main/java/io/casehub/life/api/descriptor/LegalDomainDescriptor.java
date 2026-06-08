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

public final class LegalDomainDescriptor implements LifeDomainDescriptor {
    @Override public String capability()       { return LifeCapabilities.LEGAL_DEADLINE; }
    @Override public String templateCategory() { return "legal"; }
    @Override public Set<String> workerCapabilities() { return Set.of(); }

    @Override public LifeRoutingPolicy routingPolicy() {
        return new LifeRoutingPolicy(OptionalDouble.of(0.80), OptionalInt.of(12),
                OptionalDouble.of(0.05), Optional.of("household-admin"),
                "Critical deadlines with legal consequences require highest reliability");
    }

    @Override public LifeSlaPolicy slaPolicy() {
        return new LifeSlaPolicy("household-admin", Duration.ofHours(12));
    }
}
