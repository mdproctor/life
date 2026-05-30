package io.casehub.life.app.resource;

import io.casehub.life.api.LifeActorType;
import io.casehub.life.api.request.CreateExternalActorRequest;
import io.casehub.life.api.request.UpdateExternalActorRequest;
import io.casehub.life.api.response.ExternalActorResponse;
import io.casehub.life.api.response.LifeTaskContextResponse;
import io.casehub.life.app.service.ExternalActorService;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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

    @POST
    public Response create(@Valid final CreateExternalActorRequest req) {
        final ExternalActorResponse created = service.create(req);
        return Response.created(URI.create("/external-actors/" + created.id()))
                .entity(created)
                .build();
    }

    @GET
    public List<ExternalActorResponse> list(@QueryParam("actorType") final LifeActorType actorType) {
        return service.list(actorType);
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") final UUID id) {
        return service.findById(id)
                .map(a -> Response.ok(a).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") final UUID id, @Valid final UpdateExternalActorRequest req) {
        return service.update(id, req)
                .map(a -> Response.ok(a).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") final UUID id) {
        service.delete(id);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{id}/personal-data")
    public Response erasePersonalData(@PathParam("id") final UUID id) {
        service.erase(id);
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/tasks")
    public Response listTasks(@PathParam("id") final UUID id) {
        if (service.findById(id).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        final List<LifeTaskContextResponse> tasks = service.listTasks(id);
        return Response.ok(tasks).build();
    }
}
