package io.casehub.life.app;

import io.casehub.life.api.commitment.CommitmentMode;
import io.casehub.life.api.commitment.CommitmentStatus;
import io.casehub.life.app.entity.LifeCommitmentRecord;
import io.casehub.life.app.infrastructure.LifeChannelInitializer;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.message.MessageService;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test methods are NOT @Transactional because LifeOversightResponseObserver.onMessage()
 * uses REQUIRES_NEW. If the test held an open transaction, the observer's new transaction
 * couldn't see the uncommitted LifeCommitmentRecord.
 */
@QuarkusTest
class LifeOversightResponseObserverTest {

    @Inject
    MessageService messageService;

    @Inject
    LifeChannelInitializer channelInitializer;

    @BeforeEach
    @Transactional
    void seedTemplates() {
        if (WorkItemTemplate.find("name", "household-task").count() == 0) {
            final WorkItemTemplate t = new WorkItemTemplate();
            t.id = UUID.fromString("00000000-0000-0000-0000-000000000201");
            t.name = "household-task";
            t.category = "household";
            t.priority = WorkItemPriority.MEDIUM;
            t.candidateGroups = "household-member";
            t.defaultExpiryHours = 24;
            t.createdBy = "life-system";
            t.createdAt = Instant.now();
            t.persist();
        }
    }

    @Test
    void response_toOversightGate_createsWorkItemAndFulfilsRecord() {
        // Arrange: insert and commit a pending OVERSIGHT record.
        // Not @Transactional on the test — observer needs to see the committed record.
        final String correlationId = insertOversightRecord();

        final UUID oversightChannelId = channelInitializer.channelIdFor(
                LifeChannelInitializer.OVERSIGHT_CHANNEL);

        // First dispatch a COMMAND to create the native Commitment.
        final var commandResult = messageService.dispatch(new MessageDispatch.Builder()
                .channelId(oversightChannelId)
                .sender("life-system")
                .actorType(ActorType.SYSTEM)
                .type(MessageType.COMMAND)
                .content("Approval required: Buy new car")
                .correlationId(correlationId)
                .build());

        // Dispatch the RESPONSE — observer fires synchronously via MessageObserver.
        messageService.dispatch(new MessageDispatch.Builder()
                .channelId(oversightChannelId)
                .sender("household-admin")
                .actorType(ActorType.HUMAN)
                .type(MessageType.RESPONSE)
                .content("Approved")
                .correlationId(correlationId)
                .inReplyTo(commandResult.messageId())
                .build());

        // Assert: record is FULFILLED and workItemId is set.
        final LifeCommitmentRecord updated = LifeCommitmentRecord
                .findByCorrelationId(correlationId)
                .orElseThrow();
        assertThat(updated.status).isEqualTo(CommitmentStatus.FULFILLED);
        assertThat(updated.workItemId).isNotNull();
    }

    @Test
    void response_withNoMatchingRecord_doesNothing() {
        final String unknownCorrelationId = UUID.randomUUID().toString();
        final UUID oversightChannelId = channelInitializer.channelIdFor(
                LifeChannelInitializer.OVERSIGHT_CHANNEL);

        final var commandResult = messageService.dispatch(new MessageDispatch.Builder()
                .channelId(oversightChannelId)
                .sender("life-system")
                .actorType(ActorType.SYSTEM)
                .type(MessageType.COMMAND)
                .content("Some command")
                .correlationId(unknownCorrelationId)
                .build());

        messageService.dispatch(new MessageDispatch.Builder()
                .channelId(oversightChannelId)
                .sender("household-admin")
                .actorType(ActorType.HUMAN)
                .type(MessageType.RESPONSE)
                .content("Response to unknown gate")
                .correlationId(unknownCorrelationId)
                .inReplyTo(commandResult.messageId())
                .build());

        // No LifeCommitmentRecord should exist for the unknown correlationId.
        assertThat(LifeCommitmentRecord.findByCorrelationId(unknownCorrelationId)).isEmpty();
    }

    @Transactional
    String insertOversightRecord() {
        final String correlationId = UUID.randomUUID().toString();
        final LifeCommitmentRecord record = new LifeCommitmentRecord();
        record.id = UUID.randomUUID();
        record.correlationId = correlationId;
        record.mode = CommitmentMode.OVERSIGHT;
        record.status = CommitmentStatus.PENDING_RESPONSE;
        record.channelId = LifeChannelInitializer.OVERSIGHT_CHANNEL;
        record.deadline = Instant.now().plus(24, ChronoUnit.HOURS);
        record.pendingTaskJson = """
                {"templateRef":"household-task","title":"Buy new car","externalActorId":null,"deadline":null}
                """;
        record.createdAt = record.updatedAt = Instant.now();
        record.persist();
        return correlationId;
    }
}
