package io.casehub.life.app.api;

import io.casehub.life.api.commitment.CommitmentOutcome;
import io.casehub.life.api.request.CommitmentRequest;
import io.casehub.life.api.request.CreateLifeTaskRequest;
import io.casehub.life.api.response.LifeTaskResponse;
import io.casehub.life.api.spi.LifeTaskVisibilityPolicy;
import io.casehub.life.app.commitment.LifeCommitmentService;
import io.casehub.life.app.service.LifeTaskService;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

import java.util.UUID;

@McpDomain(value = "life/tasks", app = "life", basePath = "/api/life/tasks")
@ApplicationScoped
public class LifeTaskApi {

    @Inject LifeTaskService taskService;
    @Inject LifeCommitmentService commitmentService;
    @Inject LifeTaskVisibilityPolicy visibilityPolicy;
    @Inject CurrentPrincipal currentPrincipal;

    @PlatformMutation("Create a life task")
    @RestPath("/")
    public LifeTaskResponse createTask(CreateLifeTaskRequest request) {
        return taskService.create(request);
    }

    @PlatformQuery("Get a life task by ID")
    @RestPath("/{id}")
    public LifeTaskResponse getTask(@PathParam UUID id) {
        LifeTaskResponse response = taskService.get(id);
        if (!visibilityPolicy.isVisible(response, currentPrincipal.actorId(), currentPrincipal.groups())) {
            throw new WebApplicationException(404);
        }
        return response;
    }

    @PlatformMutation("Apply a commitment to a task")
    @RestPath("/{id}/commit")
    public CommitmentOutcome commitToTask(@PathParam UUID id, CommitmentRequest request) {
        return commitmentService.applyCommitment(id, request);
    }
}
