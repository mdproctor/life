package io.casehub.life.app.service;

import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.response.ActorActivityEntry;
import io.casehub.life.api.response.PagedResponse;
import io.casehub.life.api.response.TrustHistoryEntry;
import io.casehub.life.app.entity.ExternalActor;
import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.work.runtime.model.WorkItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ExternalActorHistoryService {

    @Inject
    EntityManager em;

    @Inject
    @io.quarkus.hibernate.orm.PersistenceUnit("qhorus")
    EntityManager qhorusEm;

    @Transactional
    public PagedResponse<TrustHistoryEntry> trustHistory(UUID actorId, int page, int size) {
        page = Math.max(page, 0);
        size = Math.max(Math.min(size, 100), 1);

        TypedQuery<Long> countQuery = qhorusEm.createQuery(
                "SELECT COUNT(a) FROM LedgerAttestation a WHERE a.subjectId = :subjectId",
                Long.class);
        countQuery.setParameter("subjectId", actorId);
        long total = countQuery.getSingleResult();

        TypedQuery<LedgerAttestation> query = qhorusEm.createQuery(
                "SELECT a FROM LedgerAttestation a WHERE a.subjectId = :subjectId ORDER BY a.occurredAt ASC",
                LedgerAttestation.class);
        query.setParameter("subjectId", actorId);
        query.setFirstResult(page * size);
        query.setMaxResults(size);

        List<TrustHistoryEntry> items = query.getResultList().stream()
                .map(a -> new TrustHistoryEntry(
                        a.occurredAt, a.capabilityTag, a.trustDimension,
                        a.dimensionScore, a.verdict.name()))
                .toList();

        return new PagedResponse<>(items, page, size, total);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public PagedResponse<ActorActivityEntry> activityTimeline(UUID actorId, int page, int size) {
        page = Math.max(page, 0);
        size = Math.max(Math.min(size, 100), 1);

        long total = em.createQuery(
                        "SELECT COUNT(ctx) FROM LifeTaskContext ctx WHERE ctx.externalActorId = :actorId"
                                + " AND ctx.workItemId IN (SELECT wi.id FROM WorkItem wi)", Long.class)
                .setParameter("actorId", actorId).getSingleResult();

        List<Object[]> rows = em.createQuery(
                        "SELECT ctx, wi FROM LifeTaskContext ctx, WorkItem wi"
                                + " WHERE ctx.externalActorId = :actorId AND wi.id = ctx.workItemId"
                                + " ORDER BY wi.createdAt DESC NULLS LAST")
                .setParameter("actorId", actorId)
                .setFirstResult(page * size).setMaxResults(size).getResultList();

        List<ActorActivityEntry> items = rows.stream()
                .map(row -> {
                    LifeTaskContext ctx = (LifeTaskContext) row[0];
                    WorkItem wi = (WorkItem) row[1];
                    return new ActorActivityEntry(wi.id, wi.title, ctx.domain,
                            wi.status != null ? wi.status.name() : null,
                            wi.scope, wi.createdAt, wi.completedAt, wi.outcome);
                }).toList();

        return new PagedResponse<>(items, page, size, total);
    }

    public boolean actorExists(UUID id) {
        return ExternalActor.findByIdOptional(id).isPresent();
    }
}
