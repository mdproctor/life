package io.casehub.life.app.resource;

import io.casehub.life.api.commitment.CommitmentOutcome;
import io.casehub.life.api.request.CommitmentRequest;
import io.casehub.life.app.commitment.CommitmentConflictException;
import io.casehub.life.app.commitment.LifeCommitmentService;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Blocking
@ApplicationScoped
@Path("/life-tasks/{id}/commit")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LifeCommitmentResource {

    @Inject
    LifeCommitmentService commitmentService;

    @POST
    public Response commit(
            @PathParam("id") final UUID workItemId,
            @Valid final CommitmentRequest request) {
        try {
            final CommitmentOutcome outcome = commitmentService.applyCommitment(workItemId, request);
            return Response.ok(outcome).build();
        } catch (CommitmentConflictException e) {
            throw new WebApplicationException(e.getMessage(), 409);
        }
    }
}
