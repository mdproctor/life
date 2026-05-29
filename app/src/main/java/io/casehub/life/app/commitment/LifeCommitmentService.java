package io.casehub.life.app.commitment;

import io.casehub.life.api.commitment.CommitmentMode;
import io.casehub.life.api.commitment.CommitmentOutcome;
import io.casehub.life.api.commitment.CommitmentStatus;
import io.casehub.life.api.request.CommitmentRequest;
import io.casehub.life.api.request.OversightGateRequest;
import io.casehub.life.app.entity.ExternalActor;
import io.casehub.life.app.entity.LifeCommitmentRecord;
import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.work.runtime.model.WorkItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class LifeCommitmentService {

    @Inject
    @Any
    Instance<LifeCommitmentStrategy> strategies;

    /**
     * Applies a delegation or contractor commitment to an existing task.
     */
    @Transactional
    public CommitmentOutcome applyCommitment(final UUID workItemId, final CommitmentRequest request) {
        // XOR validation: exactly one of delegateTo or externalActorId, and deadline required.
        final boolean hasDelegateTo = request.delegateTo() != null;
        final boolean hasExternalActor = request.externalActorId() != null;
        if (hasDelegateTo == hasExternalActor || request.deadline() == null) {
            throw new WebApplicationException(
                    "CommitmentRequest must have exactly one of delegateTo or externalActorId, and deadline is required",
                    422);
        }

        // Load existing task.
        final WorkItem workItem = WorkItem.findByIdOptional(workItemId)
                .map(o -> (WorkItem) o)
                .orElseThrow(() -> new WebApplicationException("Life task not found: " + workItemId, 404));
        final LifeTaskContext taskContext = (LifeTaskContext) LifeTaskContext
                .findByIdOptional(workItemId)
                .orElseThrow(() -> new WebApplicationException("LifeTaskContext not found: " + workItemId, 404));

        // Duplicate guard: reject if a non-expired commitment already exists for this task.
        if (LifeCommitmentRecord.findByWorkItemId(workItemId).isPresent()) {
            throw new CommitmentConflictException("A commitment already exists for task: " + workItemId);
        }

        // Load ExternalActor if needed, validate it exists.
        ExternalActor externalActor = null;
        if (hasExternalActor) {
            externalActor = ExternalActor.findByIdOptional(request.externalActorId())
                    .map(o -> (ExternalActor) o)
                    .orElseThrow(() -> new WebApplicationException(
                            "ExternalActor not found: " + request.externalActorId(), 422));
        }

        final CommitmentContext ctx = hasExternalActor
                ? new ContractorContext(request, workItem, taskContext, externalActor)
                : new DelegationContext(request, workItem, taskContext);

        return dispatch(ctx);
    }

    /**
     * Creates an oversight gate (no WorkItem — task created only after RESPONSE).
     */
    @Transactional
    public CommitmentOutcome requestApproval(final OversightGateRequest request) {
        return dispatch(new OversightContext(request));
    }

    private CommitmentOutcome dispatch(final CommitmentContext ctx) {
        final List<LifeCommitmentStrategy> matched = strategies.stream()
                .filter(s -> s.applies(ctx))
                .toList();
        if (matched.isEmpty()) {
            throw new IllegalArgumentException("No strategy applies to context: " + ctx.getClass().getSimpleName());
        }
        if (matched.size() > 1) {
            throw new IllegalStateException("Ambiguous strategies for context — " + matched.size() + " match");
        }
        return matched.get(0).execute(ctx);
    }

    /**
     * Returns the active commitment status for a task, or null if none.
     */
    public CommitmentStatus getStatusForTask(final UUID workItemId) {
        return LifeCommitmentRecord.findByWorkItemId(workItemId)
                .map(r -> r.status)
                .orElse(null);
    }

    /**
     * Returns the active commitment mode for a task, or null if none.
     */
    public CommitmentMode getModeForTask(final UUID workItemId) {
        return LifeCommitmentRecord.findByWorkItemId(workItemId)
                .map(r -> r.mode)
                .orElse(null);
    }
}
