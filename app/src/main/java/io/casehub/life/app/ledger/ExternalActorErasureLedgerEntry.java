package io.casehub.life.app.ledger;

import io.casehub.ledger.runtime.model.jpa.JpaLedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Entity
@Table(name = "external_actor_erasure_ledger_entry")
@DiscriminatorValue("EXTERNAL_ACTOR_ERASURE")
public class ExternalActorErasureLedgerEntry extends JpaLedgerEntry {

    @Column(name = "erased_actor_id", nullable = false)
    public UUID erasedActorId;

    @Column(name = "contact_method", nullable = false, length = 50)
    public String contactMethod;

    @Column(name = "erased_by", nullable = false, length = 255)
    public String erasedBy;

    @Column(name = "memory_records_erased", nullable = false)
    public int memoryRecordsErased;

    @Column(name = "ledger_entries_affected", nullable = false)
    public long ledgerEntriesAffected;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
            erasedActorId != null ? erasedActorId.toString() : "",
            contactMethod != null ? contactMethod : "",
            erasedBy != null ? erasedBy : "",
            String.valueOf(memoryRecordsErased),
            String.valueOf(ledgerEntriesAffected)
        ).getBytes(StandardCharsets.UTF_8);
    }
}
