package io.casehub.life.app.engine;

import io.casehub.life.api.LifeDomain;
import io.casehub.life.app.LifeTestFixtures;
import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.life.app.infrastructure.LifeChannelInitializer;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.message.MessageService;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class LifeChannelContextProviderIntegrationTest {

    @Inject LifeChannelContextProvider provider;
    @Inject LifeChannelInitializer channelInitializer;
    @Inject MessageService messageService;
    @Inject WorkItemService workItemService;

    @BeforeEach
    @Transactional
    void setUp() {
        LifeTestFixtures.seedStandardTemplates();
    }

    @Test
    void gatherContext_returnsDelegationMessages() {
        UUID caseId = UUID.randomUUID();
        UUID delegationChannelId = channelInitializer.channelIdFor(
                LifeChannelInitializer.DELEGATION_CHANNEL);

        messageService.dispatch(MessageDispatch.builder()
                .channelId(delegationChannelId)
                .sender("life-system")
                .type(MessageType.STATUS)
                .content("Budget warning: grocery spend at 90%")
                .actorType(ActorType.SYSTEM)
                .tenancyId("278776f9-e1b0-46fb-9032-8bddebdcf9ce")
                .build());

        Map<String, Object> result = provider.gatherContext(caseId);

        @SuppressWarnings("unchecked")
        var ctx = (Map<String, Object>) result.get("channelContext");
        @SuppressWarnings("unchecked")
        var messages = (List<Map<String, Object>>) ctx.get("delegation");
        assertThat(messages).isNotEmpty();
        assertThat(messages).anyMatch(m ->
                "life-system".equals(m.get("sender"))
                && "Budget warning: grocery spend at 90%".equals(m.get("content")));
    }

    @Test
    @Transactional
    void gatherContext_includesActorChannelWhenWorkItemHasExternalActor() {
        UUID caseId = UUID.randomUUID();
        UUID externalActorId = UUID.randomUUID();

        var req = WorkItemCreateRequest.builder()
                .title("Contractor job")
                .types(List.of("contractor"))
                .priority(WorkItemPriority.MEDIUM)
                .candidateGroups("household-member")
                .createdBy("life-system")
                .callerRef("case:" + caseId + "/pi:test-plan-item")
                .scope("casehubio/life/contractor_coordination")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        WorkItem wi = workItemService.create(req);

        var ctx = new LifeTaskContext();
        ctx.workItemId = wi.id;
        ctx.domain = LifeDomain.CONTRACTOR_COORDINATION;
        ctx.externalActorId = externalActorId;
        ctx.persist();

        UUID actorChannelId = channelInitializer.ensureActorChannel(externalActorId);
        messageService.dispatch(MessageDispatch.builder()
                .channelId(actorChannelId)
                .sender("life-system")
                .type(MessageType.STATUS)
                .content("Quote received: £2500")
                .actorType(ActorType.SYSTEM)
                .tenancyId("278776f9-e1b0-46fb-9032-8bddebdcf9ce")
                .build());

        Map<String, Object> result = provider.gatherContext(caseId);

        @SuppressWarnings("unchecked")
        var channelCtx = (Map<String, Object>) result.get("channelContext");
        String actorKey = "actor/ext-" + externalActorId;
        assertThat(channelCtx).containsKey(actorKey);
        @SuppressWarnings("unchecked")
        var actorMessages = (List<Map<String, Object>>) channelCtx.get(actorKey);
        assertThat(actorMessages).anyMatch(m ->
                "Quote received: £2500".equals(m.get("content")));
    }

    @Test
    void gatherContext_omitsActorChannelWhenNoCaseWorkItems() {
        UUID caseId = UUID.randomUUID();

        Map<String, Object> result = provider.gatherContext(caseId);

        @SuppressWarnings("unchecked")
        var ctx = (Map<String, Object>) result.get("channelContext");
        assertThat(ctx).containsOnlyKeys("delegation", "oversight");
    }
}
