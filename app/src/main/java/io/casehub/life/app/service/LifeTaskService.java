package io.casehub.life.app.service;

import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.commitment.CommitmentMode;
import io.casehub.life.api.commitment.CommitmentStatus;
import io.casehub.life.api.request.CreateLifeTaskRequest;
import io.casehub.life.api.response.LifeTaskResponse;
import io.casehub.life.app.entity.ExternalActor;
import io.casehub.life.app.entity.LifeCommitmentRecord;
import io.casehub.life.app.LifeDecisionEventType;
import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.life.app.service.ledger.LifeLedgerWriter;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemCreateRequest;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.service.WorkItemService;
import io.casehub.work.runtime.service.WorkItemTemplateService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class LifeTaskService {

    @Inject
    WorkItemService workItemService;

    @Inject
    WorkItemTemplateService workItemTemplateService;

    @Inject
    LifeLedgerWriter lifeLedgerWriter;

    @Transactional
    public LifeTaskResponse create(final CreateLifeTaskRequest req) {
        // Resolve template — 422 if unknown.
        final WorkItemTemplate template = workItemTemplateService.findByRef(req.templateRef())
                .orElseThrow(() -> new WebApplicationException(
                        "Unknown templateRef: " + req.templateRef(),
                        422));

        // Validate externalActorId exists if provided — 422 if not found.
        if (req.externalActorId() != null) {
            ExternalActor.findByIdOptional(req.externalActorId())
                    .orElseThrow(() -> new WebApplicationException(
                            "ExternalActor not found: " + req.externalActorId(),
                            422));
        }

        // Validate template has candidateGroups before calling WorkItemService.create()
        // to avoid EscalateTo(empty groups) silent retry loop (GE-20260522-4e806e).
        final String candidateGroups = template.candidateGroups;
        if (candidateGroups == null || candidateGroups.isBlank()) {
            throw new WebApplicationException(
                    "Template '" + req.templateRef() + "' has no candidateGroups — cannot create SLA-backed task",
                    422);
        }

        // Compute expiry: use request.deadline() if provided, else template's defaultExpiryHours.
        final Instant expiresAt = req.deadline() != null
                ? req.deadline()
                : Instant.now().plus(
                        template.defaultExpiryHours != null ? template.defaultExpiryHours : 24L,
                        ChronoUnit.HOURS);

        // Derive LifeDomain from template category.
        final LifeDomain domain = domainFromCategory(template.category);

        // Build WorkItemCreateRequest — groups come from template only, not from caller.
        final WorkItemCreateRequest workReq = WorkItemCreateRequest.builder()
                .title(req.title())
                .category(template.category)
                .priority(template.priority)
                .candidateGroups(candidateGroups)
                .createdBy("life-system")
                .callerRef("life:task/" + req.templateRef())
                .scope("life")
                .expiresAt(expiresAt)
                .build();

        // Create WorkItem — joins this @Transactional boundary (REQUIRED semantics).
        final WorkItem workItem = workItemService.create(workReq);

        // Create LifeTaskContext supplement.
        final LifeTaskContext ctx = new LifeTaskContext();
        ctx.workItemId = workItem.id;
        ctx.domain = domain;
        ctx.externalActorId = req.externalActorId();
        ctx.persist();

        if (domain == LifeDomain.HEALTH) {
            lifeLedgerWriter.writeHealthEntry(LifeDecisionEventType.CREATED, ctx, workItem);
        } else if (domain == LifeDomain.LEGAL) {
            lifeLedgerWriter.writeLegalEntry(LifeDecisionEventType.CREATED, ctx, workItem);
        }

        return new LifeTaskResponse(
                workItem.id,
                req.templateRef(),
                domain,
                workItem.status.name(),
                req.externalActorId(),
                workItem.createdAt,
                null, null  // commitmentMode / commitmentStatus — null at creation time
        );
    }

    @Transactional
    public LifeTaskResponse get(final java.util.UUID workItemId) {
        final WorkItem workItem = WorkItem.findByIdOptional(workItemId)
                .map(o -> (WorkItem) o)
                .orElseThrow(() -> new WebApplicationException("Life task not found: " + workItemId, 404));
        final LifeTaskContext ctx = (LifeTaskContext) LifeTaskContext.findByIdOptional(workItemId)
                .orElseThrow(() -> new WebApplicationException("LifeTaskContext not found: " + workItemId, 404));
        final LifeCommitmentRecord commitment = LifeCommitmentRecord
                .findByWorkItemId(workItemId).orElse(null);
        final CommitmentMode mode = commitment != null ? commitment.mode : null;
        final CommitmentStatus status = commitment != null ? commitment.status : null;
        return new LifeTaskResponse(
                workItem.id,
                workItem.callerRef != null ? workItem.callerRef.replace("life:task/", "") : null,
                ctx.domain,
                workItem.status.name(),
                ctx.externalActorId,
                workItem.createdAt,
                mode, status
        );
    }

    private LifeDomain domainFromCategory(final String category) {
        if (category == null) return LifeDomain.HOUSEHOLD;
        return switch (category) {
            case "health" -> LifeDomain.HEALTH;
            case "contractor" -> LifeDomain.CONTRACTOR_COORDINATION;
            case "finance" -> LifeDomain.FINANCE;
            case "legal" -> LifeDomain.LEGAL;
            case "family" -> LifeDomain.FAMILY_SCHEDULING;
            case "travel" -> LifeDomain.TRAVEL;
            case "elder-care" -> LifeDomain.ELDER_CARE;
            default -> LifeDomain.HOUSEHOLD;
        };
    }
}
