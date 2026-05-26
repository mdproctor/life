package io.casehub.life.app.resource;

import io.casehub.life.api.LifeDomain;
import io.casehub.life.app.entity.LifeEvent;
import io.casehub.life.app.service.LifeEventService;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Blocking
@ApplicationScoped
@Path("/life-events")
public class LifeEventResource {

    @Inject
    LifeEventService service;

    @POST
    public Response create(@Valid final LifeEvent event) {
        final LifeEvent created = service.create(event);
        return Response.created(URI.create("/life-events/" + created.id))
                .entity(created)
                .build();
    }

    @GET
    public List<LifeEvent> list(@QueryParam("domain") final LifeDomain domain) {
        return service.list(domain);
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") final UUID id) {
        return service.findById(id)
                .map(event -> Response.ok(event).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") final UUID id) {
        service.delete(id);
        return Response.noContent().build();
    }
}
