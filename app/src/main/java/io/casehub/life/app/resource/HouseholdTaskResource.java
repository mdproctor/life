package io.casehub.life.app.resource;

import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.model.HouseholdTaskStatus;
import io.casehub.life.app.entity.HouseholdTask;
import io.casehub.life.app.service.HouseholdTaskService;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Blocking
@ApplicationScoped
@Path("/household-tasks")
public class HouseholdTaskResource {

    @Inject
    HouseholdTaskService service;

    @POST
    public Response create(@Valid final HouseholdTask task) {
        final HouseholdTask created = service.create(task);
        return Response.created(URI.create("/household-tasks/" + created.id))
                .entity(created)
                .build();
    }

    @GET
    public List<HouseholdTask> list(@QueryParam("domain") final LifeDomain domain,
                                    @QueryParam("status") final HouseholdTaskStatus status,
                                    @QueryParam("assignedTo") final String assignedTo,
                                    @QueryParam("externalActorId") final UUID externalActorId) {
        return service.list(domain, status, assignedTo, externalActorId);
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") final UUID id) {
        return service.findById(id)
                .map(task -> Response.ok(task).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") final UUID id, @Valid final HouseholdTask update) {
        return service.update(id, update)
                .map(task -> Response.ok(task).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") final UUID id) {
        service.delete(id);
        return Response.noContent().build();
    }
}
