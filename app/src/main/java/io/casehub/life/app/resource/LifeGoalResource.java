package io.casehub.life.app.resource;

import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.model.LifeGoalStatus;
import io.casehub.life.app.entity.LifeGoal;
import io.casehub.life.app.service.LifeGoalService;
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
@Path("/life-goals")
public class LifeGoalResource {

    @Inject
    LifeGoalService service;

    @POST
    public Response create(@Valid final LifeGoal goal) {
        final LifeGoal created = service.create(goal);
        return Response.created(URI.create("/life-goals/" + created.id))
                .entity(created)
                .build();
    }

    @GET
    public List<LifeGoal> list(@QueryParam("domain") final LifeDomain domain,
                               @QueryParam("status") final LifeGoalStatus status) {
        return service.list(domain, status);
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") final UUID id) {
        return service.findById(id)
                .map(goal -> Response.ok(goal).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") final UUID id, @Valid final LifeGoal update) {
        return service.update(id, update)
                .map(goal -> Response.ok(goal).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") final UUID id) {
        service.delete(id);
        return Response.noContent().build();
    }
}
