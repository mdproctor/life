package io.casehub.life.app.commitment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.life.api.commitment.CommitmentMode;
import io.casehub.life.api.commitment.CommitmentOutcome;
import io.casehub.life.api.commitment.CommitmentStatus;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.app.LifeDecisionEventType;
import io.casehub.life.app.entity.LifeCommitmentRecord;
import io.casehub.life.app.infrastructure.LifeChannelInitializer;
import io.casehub.life.app.service.ledger.DomainLedgerHandler;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.message.MessageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class OversightGateStrategy implements LifeCommitmentStrategy {

    @Inject
    MessageService messageService;

    @Inject
    LifeChannelInitializer channelInitializer;

    @Inject
    ObjectMapper objectMapper;

    @Inject @Any
    Instance<DomainLedgerHandler> ledgerHandlers;

    @Override
    public boolean applies(final CommitmentContext context) {
        return context instanceof OversightContext;
    }

    @Override
    public CommitmentOutcome execute(final CommitmentContext context) {
        final var oc = (OversightContext) context;

        // Duplicate gate guard: best-effort dedup by title+templateRef key.
        final String taskKey = oc.request().pendingTask().title()
                + ":" + oc.request().pendingTask().templateRef();
        final boolean duplicate = LifeCommitmentRecord
                .find("mode = ?1 and status = ?2 and delegateTo = ?3",
                        CommitmentMode.OVERSIGHT, CommitmentStatus.PENDING_RESPONSE, taskKey)
                .count() > 0;
        if (duplicate) {
            throw new CommitmentConflictException(
                    "Oversight gate already pending for: " + oc.request().pendingTask().title());
        }

        final UUID channelId = channelInitializer.channelIdFor(LifeChannelInitializer.OVERSIGHT_CHANNEL);
        final String correlationId = UUID.randomUUID().toString();

        messageService.dispatch(new MessageDispatch.Builder()
                .channelId(channelId)
                .sender("life-system")
                .actorType(ActorType.SYSTEM)
                .type(MessageType.COMMAND)
                .content("Approval required: " + oc.request().pendingTask().title())
                .correlationId(correlationId)
                .deadline(oc.request().deadline())
                .build());

        final String pendingTaskJson;
        try {
            pendingTaskJson = objectMapper.writeValueAsString(oc.request().pendingTask());
        } catch (JsonProcessingException e) {
            throw new WebApplicationException("Failed to serialize pendingTask", e, 500);
        }

        final var record = new LifeCommitmentRecord();
        record.id = UUID.randomUUID();
        record.correlationId = correlationId;
        record.mode = CommitmentMode.OVERSIGHT;
        record.status = CommitmentStatus.PENDING_RESPONSE;
        // workItemId null until household-admin RESPONSE
        record.channelId = LifeChannelInitializer.OVERSIGHT_CHANNEL;
        record.deadline = oc.request().deadline();
        record.delegateTo = taskKey;       // repurposed as dedup key for oversight gates
        record.amountThreshold = oc.request().amountThreshold();
        record.purchaseCategory = oc.request().purchaseCategory();
        record.pendingTaskJson = pendingTaskJson;
        record.createdAt = record.updatedAt = Instant.now();
        record.persist();

        ledgerHandlers.stream()
                .filter(h -> h.domain() == LifeDomain.FINANCE)
                .findFirst()
                .ifPresent(h -> h.writeEntry(LifeDecisionEventType.CREATED, record));

        return new CommitmentOutcome(record.id, correlationId,
                CommitmentMode.OVERSIGHT, CommitmentStatus.PENDING_RESPONSE);
    }
}
