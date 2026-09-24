package io.casehub.life.app.api;

import io.casehub.life.api.LifeCaseStatus;
import io.casehub.life.api.LifeCaseType;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.request.CreateLifeCaseRequest;
import io.casehub.life.api.response.CbrPrecedentResponse;
import io.casehub.life.api.response.ChannelMessageResponse;
import io.casehub.life.api.response.LifeCaseDetailResponse;
import io.casehub.life.api.response.LifeCaseResponse;
import io.casehub.life.api.response.LifeCommitmentResponse;
import io.casehub.life.api.response.PagedResponse;
import io.casehub.life.api.response.PendingActionResponse;
import io.casehub.life.api.response.RoutingDecisionResponse;
import io.casehub.life.app.engine.LifeCaseService;
import io.casehub.life.app.service.LifeCaseQueryService;
import io.casehub.life.app.service.LifeCbrQueryService;
import io.casehub.life.app.service.LifeChannelQueryService;
import io.casehub.life.app.service.LifeRoutingQueryService;
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

@McpDomain(value = "life/cases", app = "life", basePath = "/api/life/cases", summary = "Life case management — long-running personal cases")
@ApplicationScoped
public class LifeCaseApi {

    @Inject LifeCaseService lifeCaseService;
    @Inject LifeCaseQueryService queryService;
    @Inject LifeRoutingQueryService routingQueryService;
    @Inject LifeCbrQueryService cbrQueryService;
    @Inject LifeChannelQueryService channelQueryService;

    @PlatformMutation("Create a life case")
    @RestPath("/")
    public LifeCaseResponse createCase(CreateLifeCaseRequest request) {
        return lifeCaseService.startCase(request);
    }

    @PlatformQuery("List life cases")
    @RestPath("/")
    public PagedResponse<LifeCaseResponse> listCases(
            @QueryParam("domain") LifeDomain domain,
            @QueryParam("status") LifeCaseStatus status,
            @QueryParam("caseType") LifeCaseType caseType,
            @QueryParam("page") int page,
            @QueryParam("size") int size) {
        return queryService.listCases(domain, status, caseType, page, size);
    }

    @PlatformQuery("Get a life case by ID")
    @RestPath("/{id}")
    public LifeCaseDetailResponse getCase(@PathParam UUID id) {
        return queryService.findById(id)
                .orElseThrow(NotFoundException::new);
    }

    @PlatformQuery("List tasks for a case")
    @RestPath("/{id}/tasks")
    public PagedResponse<PendingActionResponse> listCaseTasks(@PathParam UUID id) {
        List<PendingActionResponse> tasks = queryService.findTasksByCase(id)
                .orElseThrow(NotFoundException::new);
        return new PagedResponse<>(tasks, 0, tasks.size(), tasks.size());
    }

    @PlatformQuery("List commitments for a case")
    @RestPath("/{id}/commitments")
    public List<LifeCommitmentResponse> listCaseCommitments(@PathParam UUID id) {
        return queryService.findCommitmentsByCase(id)
                .orElseThrow(NotFoundException::new);
    }

    @PlatformQuery("Get routing decisions for a case")
    @RestPath("/{id}/routing")
    public List<RoutingDecisionResponse> listCaseRouting(@PathParam UUID id) {
        queryService.findById(id).orElseThrow(NotFoundException::new);
        return routingQueryService.findRoutingByCase(id)
                .orElseThrow(NotFoundException::new);
    }

    @PlatformQuery("Get CBR precedents for a case")
    @RestPath("/{id}/cbr")
    public List<CbrPrecedentResponse> listCaseCbr(@PathParam UUID id) {
        queryService.findById(id).orElseThrow(NotFoundException::new);
        return cbrQueryService.findPrecedentsByCase(id)
                .orElseThrow(NotFoundException::new);
    }

    @PlatformQuery("Get channels for a case")
    @RestPath("/{id}/channels")
    public List<ChannelMessageResponse> listCaseChannels(@PathParam UUID id) {
        queryService.findById(id).orElseThrow(NotFoundException::new);
        return channelQueryService.findChannelMessagesByCase(id)
                .orElseThrow(NotFoundException::new);
    }
}
