package io.casehub.life.app.cbr;

import io.casehub.neocortex.memory.cbr.PlanAdapter;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@QuarkusTest
class LifePlanAdapterQuarkusTest {

    @Inject
    PlanAdapter planAdapter;

    @Test
    void planAdapter_resolvesToLifePlanAdapter() {
        assertInstanceOf(LifePlanAdapter.class, planAdapter);
    }
}
