package io.casehub.life.app.commitment;

import io.casehub.life.api.commitment.CommitmentMode;
import io.casehub.life.api.commitment.CommitmentOutcome;
import io.casehub.life.api.commitment.CommitmentStatus;
import io.casehub.life.app.entity.LifeCommitmentRecord;
import io.casehub.life.app.infrastructure.LifeChannelInitializer;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.message.MessageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class DelegationCommitmentStrategy implements LifeCommitmentStrategy {

    @Inject
    MessageService messageService;

    @Inject
    LifeChannelInitializer channelInitializer;

    @Override
    public boolean applies(final CommitmentContext context) {
        if (!(context instanceof DelegationContext dc)) return false;
        return dc.request().delegateTo() != null && dc.request().deadline() != null;
    }

    @Override
    public CommitmentOutcome execute(final CommitmentContext context) {
        final var dc = (DelegationContext) context;
        final String correlationId = UUID.randomUUID().toString();
        // DELEGATION_CHANNEL is pre-initialized at startup — look up its UUID.
        final UUID channelId = channelInitializer.channelIdFor(LifeChannelInitializer.DELEGATION_CHANNEL);

        messageService.dispatch(new MessageDispatch.Builder()
                .channelId(channelId)
                .sender("life-system")
                .actorType(ActorType.SYSTEM)
                .type(MessageType.COMMAND)
                .content("Task delegation: " + dc.workItem().title)
                .correlationId(correlationId)
                .target(dc.request().delegateTo())
                .deadline(dc.request().deadline())
                .build());

        final var record = new LifeCommitmentRecord();
        record.id = UUID.randomUUID();
        record.correlationId = correlationId;
        record.mode = CommitmentMode.DELEGATION;
        record.status = CommitmentStatus.PENDING_RESPONSE;
        record.workItemId = dc.workItem().id;
        record.delegateTo = dc.request().delegateTo();
        record.channelId = LifeChannelInitializer.DELEGATION_CHANNEL;
        record.deadline = dc.request().deadline();
        record.createdAt = record.updatedAt = Instant.now();
        record.persist();

        return new CommitmentOutcome(record.id, correlationId,
                CommitmentMode.DELEGATION, CommitmentStatus.PENDING_RESPONSE);
    }
}
