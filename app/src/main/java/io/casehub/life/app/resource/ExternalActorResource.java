package io.casehub.life.app.resource;

import io.casehub.life.api.LifeActorType;
import io.casehub.life.api.model.HouseholdTaskStatus;
import io.casehub.life.app.entity.ExternalActor;
import io.casehub.life.app.entity.HouseholdTask;
import io.casehub.life.app.service.ExternalActorService;
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
@Path("/external-actors")
public class ExternalActorResource {

    @Inject
    ExternalActorService service;

    @POST
    public Response create(@Valid final ExternalActor actor) {
        final ExternalActor created = service.create(actor);
        return Response.created(URI.create("/external-actors/" + created.id))
                .entity(created)
                .build();
    }

    @GET
    public List<ExternalActor> list(@QueryParam("actorType") final LifeActorType actorType) {
        return service.list(actorType);
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") final UUID id) {
        return service.findById(id)
                .map(actor -> Response.ok(actor).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") final UUID id, @Valid final ExternalActor update) {
        return service.update(id, update)
                .map(actor -> Response.ok(actor).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") final UUID id) {
        service.delete(id);
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/tasks")
    public Response listTasks(@PathParam("id") final UUID id,
                              @QueryParam("status") final HouseholdTaskStatus status) {
        if (service.findById(id).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        final List<HouseholdTask> tasks = service.listTasks(id, status);
        return Response.ok(tasks).build();
    }
}
