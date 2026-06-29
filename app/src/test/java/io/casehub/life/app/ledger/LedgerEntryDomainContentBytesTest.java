package io.casehub.life.app.ledger;

import io.casehub.life.app.LifeDecisionEventType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerEntryDomainContentBytesTest {

    @Test
    void healthDecision_includesAllFields() {
        var entry = new HealthDecisionLedgerEntry();
        entry.workItemId = UUID.fromString("11111111-0000-0000-0000-000000000001");
        entry.providerId = UUID.fromString("22222222-0000-0000-0000-000000000002");
        entry.taskCategory = "GP_APPOINTMENT";
        entry.slaDeadline = Instant.parse("2026-06-20T10:00:00Z");
        entry.eventType = LifeDecisionEventType.COMPLETED;
        entry.outcome = "attended";

        var content = new String(entry.domainContentBytes(), StandardCharsets.UTF_8);

        assertThat(content).contains("11111111-0000-0000-0000-000000000001");
        assertThat(content).contains("22222222-0000-0000-0000-000000000002");
        assertThat(content).contains("GP_APPOINTMENT");
        assertThat(content).contains("COMPLETED");
        assertThat(content).contains("attended");
        assertThat(content).contains("|");
    }

    @Test
    void healthDecision_nullableFieldsProduceEmptySegment() {
        var entry = new HealthDecisionLedgerEntry();
        entry.workItemId = UUID.randomUUID();
        entry.taskCategory = "GP_APPOINTMENT";
        entry.slaDeadline = Instant.now();
        entry.eventType = LifeDecisionEventType.CREATED;

        var content = new String(entry.domainContentBytes(), StandardCharsets.UTF_8);

        assertThat(content).isNotEmpty();
        var parts = content.split("\\|", -1);
        assertThat(parts).hasSize(6);
        assertThat(parts[1]).isEmpty(); // providerId null → ""
        assertThat(parts[5]).isEmpty(); // outcome null → ""
    }

    @Test
    void financial_includesAllFields() {
        var entry = new FinancialDecisionLedgerEntry();
        entry.workItemId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        entry.oversightRef = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
        entry.amountThreshold = new BigDecimal("250.00");
        entry.purchaseCategory = "HOME_APPLIANCE";
        entry.approvedBy = "household-admin";
        entry.eventType = LifeDecisionEventType.COMPLETED;

        var content = new String(entry.domainContentBytes(), StandardCharsets.UTF_8);

        assertThat(content).contains("aaaaaaaa-0000-0000-0000-000000000001");
        assertThat(content).contains("bbbbbbbb-0000-0000-0000-000000000002");
        assertThat(content).contains("250.00");
        assertThat(content).contains("HOME_APPLIANCE");
        assertThat(content).contains("household-admin");
        assertThat(content).contains("COMPLETED");
    }

    @Test
    void legal_includesAllFields() {
        var entry = new LegalActionLedgerEntry();
        entry.workItemId = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
        entry.legalObligation = "TAX_FILING";
        entry.filingDeadline = Instant.parse("2026-01-31T23:59:59Z");
        entry.jurisdiction = "UK";
        entry.eventType = LifeDecisionEventType.SLA_BREACH;
        entry.actionTaken = "filed-late";

        var content = new String(entry.domainContentBytes(), StandardCharsets.UTF_8);

        assertThat(content).contains("cccccccc-0000-0000-0000-000000000001");
        assertThat(content).contains("TAX_FILING");
        assertThat(content).contains("UK");
        assertThat(content).contains("SLA_BREACH");
        assertThat(content).contains("filed-late");
    }

    @Test
    void erasure_includesAllFields() {
        var entry = new ExternalActorErasureLedgerEntry();
        entry.erasedActorId = UUID.fromString("dddddddd-0000-0000-0000-000000000001");
        entry.contactMethod = "SMS";
        entry.erasedBy = "household-admin";

        var content = new String(entry.domainContentBytes(), StandardCharsets.UTF_8);

        assertThat(content).contains("dddddddd-0000-0000-0000-000000000001");
        assertThat(content).contains("SMS");
        assertThat(content).contains("household-admin");
    }
}
