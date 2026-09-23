package io.casehub.life.app.api;

import io.casehub.life.api.LifeActorType;
import io.casehub.life.api.request.CreateExternalActorRequest;
import io.casehub.life.api.request.UpdateExternalActorRequest;
import io.casehub.life.api.response.ActorActivityEntry;
import io.casehub.life.api.response.ErasureResponse;
import io.casehub.life.api.response.ExternalActorResponse;
import io.casehub.life.api.response.LifeTaskContextResponse;
import io.casehub.life.api.response.PagedResponse;
import io.casehub.life.api.response.TrustHistoryEntry;
import io.casehub.life.app.service.ExternalActorHistoryService;
import io.casehub.life.app.service.ExternalActorService;
import io.casehub.life.app.service.LifeGdprErasureService;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.QueryParam;

import java.util.List;
import java.util.UUID;

@McpDomain(value = "life/actors", app = "life", basePath = "/api/life/actors")
@ApplicationScoped
public class LifeExternalActorApi {

    @Inject ExternalActorService actorService;
    @Inject ExternalActorHistoryService historyService;
    @Inject LifeGdprErasureService gdprErasureService;
    @Inject CurrentPrincipal currentPrincipal;

    @PlatformMutation("Create an external actor")
    @RestPath("/")
    public ExternalActorResponse createActor(CreateExternalActorRequest request) {
        return actorService.create(request);
    }

    @PlatformQuery("Search external actors")
    @RestPath("/")
    public PagedResponse<ExternalActorResponse> listActors(
            @QueryParam("actorType") LifeActorType actorType,
            @QueryParam("name") String name,
            @QueryParam("contactMethod") String contactMethod,
            @QueryParam("erasedOnly") boolean erasedOnly,
            @QueryParam("page") int page,
            @QueryParam("size") int size) {
        return actorService.search(name, actorType, contactMethod, erasedOnly, page, size);
    }

    @PlatformQuery("Get an external actor by ID")
    @RestPath("/{id}")
    public ExternalActorResponse getActor(@PathParam UUID id) {
        return actorService.findById(id)
                .orElseThrow(NotFoundException::new);
    }

    @PlatformMutation("Update an external actor")
    @RestPath("/{id}")
    public ExternalActorResponse updateActor(@PathParam UUID id, UpdateExternalActorRequest request) {
        return actorService.update(id, request)
                .orElseThrow(NotFoundException::new);
    }

    @PlatformMutation("Delete an external actor")
    @RestPath("/{id}/delete")
    public void deleteActor(@PathParam UUID id) {
        actorService.delete(id);
    }

    @PlatformMutation("Erase personal data (GDPR)")
    @RestPath("/{id}/personal-data/erase")
    public ErasureResponse erasePersonalData(@PathParam UUID id) {
        return gdprErasureService.erase(id, currentPrincipal.actorId());
    }

    @PlatformQuery("Get tasks for an external actor")
    @RestPath("/{id}/tasks")
    public List<LifeTaskContextResponse> listActorTasks(@PathParam UUID id) {
        actorService.findById(id).orElseThrow(NotFoundException::new);
        return actorService.listTasks(id);
    }

    @PlatformQuery("Get trust history for an external actor")
    @RestPath("/{id}/trust-history")
    public PagedResponse<TrustHistoryEntry> trustHistory(@PathParam UUID id,
                                @QueryParam("page") int page,
                                @QueryParam("size") int size) {
        if (!historyService.actorExists(id)) {
            throw new NotFoundException();
        }
        return historyService.trustHistory(id, page, size);
    }

    @PlatformQuery("Get activity timeline for an external actor")
    @RestPath("/{id}/activity")
    public PagedResponse<ActorActivityEntry> activityTimeline(@PathParam UUID id,
                                    @QueryParam("page") int page,
                                    @QueryParam("size") int size) {
        if (!historyService.actorExists(id)) {
            throw new NotFoundException();
        }
        return historyService.activityTimeline(id, page, size);
    }
}
