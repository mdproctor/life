package io.casehub.life.app.ledger;

import io.casehub.ledger.runtime.model.LedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Entity
@Table(name = "external_actor_erasure_ledger_entry")
@DiscriminatorValue("EXTERNAL_ACTOR_ERASURE")
public class ExternalActorErasureLedgerEntry extends LedgerEntry {

    @Column(name = "erased_actor_id", nullable = false)
    public UUID erasedActorId;

    @Column(name = "contact_method", nullable = false, length = 50)
    public String contactMethod;

    @Column(name = "erased_by", nullable = false, length = 255)
    public String erasedBy;

    @Column(name = "memory_records_erased", nullable = false)
    public int memoryRecordsErased;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
            erasedActorId != null ? erasedActorId.toString() : "",
            contactMethod != null ? contactMethod : "",
            erasedBy != null ? erasedBy : ""
        ).getBytes(StandardCharsets.UTF_8);
    }
}
