package io.casehub.life.app.resource;

import io.casehub.life.api.HouseholdGroups;
import io.casehub.life.api.LifeActorType;
import io.casehub.life.api.request.CreateExternalActorRequest;
import io.casehub.life.api.request.UpdateExternalActorRequest;
import io.casehub.life.api.response.ErasureResponse;
import io.casehub.life.api.response.ExternalActorResponse;
import io.casehub.life.api.response.LifeTaskContextResponse;
import io.casehub.life.api.response.PagedResponse;
import io.casehub.life.app.service.ExternalActorHistoryService;
import io.casehub.life.app.service.ExternalActorService;
import io.casehub.life.app.service.LifeGdprErasureService;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Blocking
@ApplicationScoped
@Path("/external-actors")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ExternalActorResource {

    @Inject
    ExternalActorService service;

    @Inject
    LifeGdprErasureService gdprErasureService;

    @Inject
    CurrentPrincipal currentPrincipal;
    @Inject
    ExternalActorHistoryService historyService;

    @POST
    @RolesAllowed({HouseholdGroups.ADMIN, HouseholdGroups.MEMBER})
    public Response create(@Valid final CreateExternalActorRequest req) {
        final ExternalActorResponse created = service.create(req);
        return Response.created(URI.create("/external-actors/" + created.id()))
                .entity(created)
                .build();
    }

    @GET
    @RolesAllowed({HouseholdGroups.ADMIN, HouseholdGroups.MEMBER, HouseholdGroups.JUNIOR})
    public PagedResponse<ExternalActorResponse> list(
            @QueryParam("actorType") final LifeActorType actorType,
            @QueryParam("name") final String name,
            @QueryParam("contactMethod") final String contactMethod,
            @QueryParam("erasedOnly") @DefaultValue("false") final boolean erasedOnly,
            @QueryParam("page") @DefaultValue("0") final int page,
            @QueryParam("size") @DefaultValue("20") final int size) {
        return service.search(name, actorType, contactMethod, erasedOnly, page, size);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({HouseholdGroups.ADMIN, HouseholdGroups.MEMBER, HouseholdGroups.JUNIOR})
    public Response get(@PathParam("id") final UUID id) {
        return service.findById(id)
                      .map(a -> Response.ok(a).build())
                      .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed(HouseholdGroups.ADMIN)
    public Response update(@PathParam("id") final UUID id, @Valid final UpdateExternalActorRequest req) {
        return service.update(id, req)
                .map(a -> Response.ok(a).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed(HouseholdGroups.ADMIN)
    public Response delete(@PathParam("id") final UUID id) {
        service.delete(id);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{id}/personal-data")
    @RolesAllowed(HouseholdGroups.ADMIN)
    public Response erasePersonalData(@PathParam("id") final UUID id) {
        ErasureResponse result = gdprErasureService.erase(id, currentPrincipal.actorId());
        return Response.ok(result).build();
    }

    @GET
    @Path("/{id}/tasks")
    @RolesAllowed({HouseholdGroups.ADMIN, HouseholdGroups.MEMBER})
    public Response listTasks(@PathParam("id") final UUID id) {
        if (service.findById(id).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        final List<LifeTaskContextResponse> tasks = service.listTasks(id);
        return Response.ok(tasks).build();
    }

    @GET
    @Path("/{id}/trust-history")
    @RolesAllowed({HouseholdGroups.ADMIN, HouseholdGroups.MEMBER, HouseholdGroups.JUNIOR})
    public Response trustHistory(
            @PathParam("id") final UUID id,
            @QueryParam("page") @DefaultValue("0") final int page,
            @QueryParam("size") @DefaultValue("20") final int size) {
        if (!historyService.actorExists(id)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(historyService.trustHistory(id, page, size)).build();
    }

    @GET
    @Path("/{id}/activity")
    @RolesAllowed({HouseholdGroups.ADMIN, HouseholdGroups.MEMBER, HouseholdGroups.JUNIOR})
    public Response activityTimeline(
            @PathParam("id") final UUID id,
            @QueryParam("page") @DefaultValue("0") final int page,
            @QueryParam("size") @DefaultValue("20") final int size) {
        if (!historyService.actorExists(id)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(historyService.activityTimeline(id, page, size)).build();
    }


}
