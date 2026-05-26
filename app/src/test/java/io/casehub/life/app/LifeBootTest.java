package io.casehub.life.app;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LifeBootTest {

    @Test
    void applicationStartsSuccessfully() {
        // CDI context starts, all entity/service/resource beans wire correctly,
        // and H2 drop-and-create schema generation succeeds.
        // Note: does NOT validate Flyway migrations — tests use drop-and-create
        // to avoid the V1.0.0 conflict between casehub-engine-persistence-hibernate
        // and casehub-work at classpath:db/migration.
    }
}
