package io.casehub.life.app.observer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.life.api.commitment.CommitmentMode;
import io.casehub.life.api.commitment.CommitmentStatus;
import io.casehub.life.api.request.CreateLifeTaskRequest;
import io.casehub.life.api.response.LifeTaskResponse;
import io.casehub.life.app.entity.LifeCommitmentRecord;
import io.casehub.life.app.infrastructure.LifeChannelInitializer;
import io.casehub.life.app.service.LifeTaskService;
import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * Bridges a household-admin RESPONSE on life/oversight back to task creation.
 * When a RESPONSE arrives for an OVERSIGHT LifeCommitmentRecord:
 *   1. Deserializes the pending CreateLifeTaskRequest
 *   2. Creates the WorkItem + LifeTaskContext via LifeTaskService
 *   3. Links the new workItemId into LifeCommitmentRecord
 *
 * MessageObserver is application-wide — type + channelName guards are required
 * (GE-20260517-f28d15). MessageReceivedEvent.channelName() is the channel name string.
 */
@ApplicationScoped
public class LifeOversightResponseObserver implements MessageObserver {

    private static final Logger LOG = Logger.getLogger(LifeOversightResponseObserver.class);

    @Inject
    LifeTaskService lifeTaskService;

    @Inject
    ObjectMapper objectMapper;

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onMessage(final MessageReceivedEvent event) {
        // Application-wide guard: only RESPONSE on the oversight channel.
        if (event.messageType() != MessageType.RESPONSE) return;
        if (!LifeChannelInitializer.OVERSIGHT_CHANNEL.equals(event.channelName())) return;
        if (event.correlationId() == null) return;

        LifeCommitmentRecord.findByCorrelationId(event.correlationId())
                .filter(r -> r.mode == CommitmentMode.OVERSIGHT
                        && r.status == CommitmentStatus.PENDING_RESPONSE)
                .ifPresent(record -> {
                    try {
                        record.approvedBy = event.senderId();
                        final CreateLifeTaskRequest pending = objectMapper.readValue(
                                record.pendingTaskJson, CreateLifeTaskRequest.class);
                        final LifeTaskResponse created = lifeTaskService.create(pending);
                        record.workItemId = created.workItemId();
                        record.status = CommitmentStatus.FULFILLED;
                        record.updatedAt = Instant.now();
                        record.persist();
                    } catch (JsonProcessingException e) {
                        LOG.errorf(e,
                                "Failed to deserialize pendingTaskJson for oversight correlationId %s — gate remains PENDING_RESPONSE",
                                event.correlationId());
                    }
                });
    }
}
