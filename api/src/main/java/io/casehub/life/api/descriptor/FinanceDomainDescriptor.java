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

public final class FinanceDomainDescriptor implements LifeDomainDescriptor {
    @Override public String capability()       { return LifeCapabilities.FINANCIAL_PLANNING; }
    @Override public String templateCategory() { return "finance"; }

    @Override public Set<String> workerCapabilities() {
        return Set.of("gather-data", "analyse-anomalies", "escalate-anomalies",
                      "oversight-response", "produce-report");
    }

    @Override public LifeRoutingPolicy routingPolicy() {
        return new LifeRoutingPolicy(OptionalDouble.of(0.70), OptionalInt.of(10),
                OptionalDouble.of(0.10), Optional.of("household-admin"),
                "Financial decisions require cost accuracy but tolerate wider margin");
    }

    @Override public LifeSlaPolicy slaPolicy() {
        return new LifeSlaPolicy("household-admin", Duration.ofHours(48));
    }
}
