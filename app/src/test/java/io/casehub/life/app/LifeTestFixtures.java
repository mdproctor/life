package io.casehub.life.app;

import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemTemplate;

import java.util.UUID;

/**
 * Shared WorkItemTemplate seeding utility for @QuarkusTest classes.
 *
 * All methods are static and expect a Panache-active transaction to be in scope
 * (provided by @BeforeEach @Transactional in the calling test class).
 *
 * Idempotent: each seed checks for existence by name before persisting.
 * Canonical UUIDs are deterministic to support test isolation analysis.
 */
public final class LifeTestFixtures {

    private LifeTestFixtures() {}

    public static void seedStandardTemplates() {
        seedIfAbsent("00000000-0000-0000-0000-000000000001",
                "household-task", "household", 24, "household-member", WorkItemPriority.MEDIUM, null);
        seedIfAbsent("00000000-0000-0000-0000-000000000002",
                "health-appointment", "health", 48, "household-member", WorkItemPriority.MEDIUM, null);
        seedIfAbsent("00000000-0000-0000-0000-000000000003",
                "contractor-coordination", "contractor", 72, "household-member", WorkItemPriority.MEDIUM, null);
    }

    public static void seedEscalationTemplate() {
        seedIfAbsent("00000000-0000-0000-0000-000000000004",
                "life-escalation", "household", 24, "household-admin", WorkItemPriority.HIGH,
                "Commitment deadline passed — manual action required by household-admin");
    }

    private static void seedIfAbsent(String id, String name, String category,
                                     int expiryHours, String candidateGroups,
                                     WorkItemPriority priority, String description) {
        if (WorkItemTemplate.find("name", name).count() == 0) {
            WorkItemTemplate t = new WorkItemTemplate();
            t.id = UUID.fromString(id);
            t.name = name;
            t.typePaths = category;
            t.priority = priority;
            t.candidateGroups = candidateGroups;
            t.defaultExpiryHours = expiryHours;
            t.description = description;
            t.createdBy = "life-system";
            t.tenancyId = "278776f9-e1b0-46fb-9032-8bddebdcf9ce";
            t.persist();
        }
    }
}
