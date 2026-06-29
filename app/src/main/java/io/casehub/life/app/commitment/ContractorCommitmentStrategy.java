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
public class ContractorCommitmentStrategy implements LifeCommitmentStrategy {

    @Inject
    MessageService messageService;

    @Inject
    LifeChannelInitializer channelInitializer;

    @Override
    public boolean applies(final CommitmentContext context) {
        if (!(context instanceof ContractorContext cc)) return false;
        return cc.request().deadline() != null;
    }

    @Override
    public CommitmentOutcome execute(final CommitmentContext context) {
        final var cc = (ContractorContext) context;
        final String actorChannelName = "life/actor/ext-" + cc.externalActor().id;
        final UUID actorChannelId = channelInitializer.ensureActorChannel(cc.externalActor().id);
        final String correlationId = UUID.randomUUID().toString();

        messageService.dispatch(new MessageDispatch.Builder()
                .channelId(actorChannelId)
                .sender("life-system")
                .actorType(ActorType.SYSTEM)
                .type(MessageType.COMMAND)
                .content("Contractor commitment: " + cc.externalActor().name + " by " + cc.request().deadline())
                .correlationId(correlationId)
                .deadline(cc.request().deadline())
                .build());

        final var record = new LifeCommitmentRecord();
        record.id = UUID.randomUUID();
        record.correlationId = correlationId;
        record.mode = CommitmentMode.CONTRACTOR;
        record.status = CommitmentStatus.PENDING_RESPONSE;
        record.workItemId = cc.workItem().id;
        record.externalActorId = cc.externalActor().id;
        record.domain = cc.taskContext() != null ? cc.taskContext().domain : null;
        record.channelId = actorChannelName;
        record.deadline = cc.request().deadline();
        record.createdAt = record.updatedAt = Instant.now();
        record.persist();

        return new CommitmentOutcome(record.id, correlationId,
                CommitmentMode.CONTRACTOR, CommitmentStatus.PENDING_RESPONSE);
    }
}
