package io.casehub.life.app.commitment;

import io.casehub.life.api.request.CommitmentRequest;
import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.work.runtime.model.WorkItem;

public record DelegationContext(
        CommitmentRequest request,
        WorkItem workItem,
        LifeTaskContext taskContext
) implements CommitmentContext {}
