package io.casehub.life.app.entity;

import io.casehub.life.api.commitment.CommitmentMode;
import io.casehub.life.api.commitment.CommitmentStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "life_commitment_record")
public class LifeCommitmentRecord extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "correlation_id", nullable = false, unique = true, length = 255)
    public String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    public CommitmentMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    public CommitmentStatus status;

    @Column(name = "work_item_id")
    public UUID workItemId;              // null for OVERSIGHT until RESPONSE fulfills gate

    @Column(name = "external_actor_id")
    public UUID externalActorId;        // CONTRACTOR only

    @Column(name = "delegate_to", length = 255)
    public String delegateTo;           // DELEGATION: principal id; OVERSIGHT: dedup key

    @Column(name = "channel_id", nullable = false, length = 255)
    public String channelId;

    public Instant deadline;

    @Column(name = "pending_task_json", columnDefinition = "TEXT")
    public String pendingTaskJson;      // OVERSIGHT only — serialized CreateLifeTaskRequest

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Column(name = "approved_by", length = 255)
    public String approvedBy;

    @Column(name = "amount_threshold", precision = 15, scale = 2)
    public java.math.BigDecimal amountThreshold;

    @Column(name = "purchase_category", length = 100)
    public String purchaseCategory;

    public static Optional<LifeCommitmentRecord> findByCorrelationId(final String correlationId) {
        return find("correlationId", correlationId).firstResultOptional();
    }

    public static Optional<LifeCommitmentRecord> findByWorkItemId(final UUID workItemId) {
        return find("workItemId = ?1 and status != ?2",
                workItemId, CommitmentStatus.EXPIRED).firstResultOptional();
    }

    /**
     * Returns all PENDING_RESPONSE records on the channel whose deadline has passed.
     * Used by LifeWatchdogAlertObserver — WatchdogAlertEvent carries notificationChannel, not correlationId.
     */
    public static List<LifeCommitmentRecord> findExpiredPendingByChannel(
            final String channelId, final Instant now) {
        return find("channelId = ?1 and status = ?2 and deadline <= ?3",
                channelId, CommitmentStatus.PENDING_RESPONSE, now).list();
    }
}
