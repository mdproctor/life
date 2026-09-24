package io.casehub.life.app.api;

import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.commitment.CommitmentOutcome;
import io.casehub.life.api.request.OversightGateRequest;
import io.casehub.life.api.response.BriefingResponse;
import io.casehub.life.api.response.PagedResponse;
import io.casehub.life.api.response.PendingActionResponse;
import io.casehub.life.app.commitment.LifeCommitmentService;
import io.casehub.life.app.service.DashboardService;
import io.casehub.life.app.service.PendingActionsService;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.QueryParam;

@McpDomain(value = "life/dashboard", app = "life", basePath = "/api/life/dashboard", summary = "Life dashboard — aggregated views and summaries")
@ApplicationScoped
public class LifeDashboardApi {

    @Inject DashboardService dashboardService;
    @Inject PendingActionsService pendingActionsService;
    @Inject LifeCommitmentService commitmentService;

    @PlatformQuery("Get household briefing")
    @RestPath("/briefing")
    public BriefingResponse briefing() {
        return dashboardService.buildBriefing();
    }

    @PlatformQuery("List pending actions")
    @RestPath("/pending-actions")
    public PagedResponse<PendingActionResponse> pendingActions(
            @QueryParam("domain") LifeDomain domain,
            @QueryParam("candidateGroup") String candidateGroup,
            @QueryParam("dueSoonHours") int dueSoonHours,
            @QueryParam("page") int page,
            @QueryParam("size") int size) {
        return pendingActionsService.findPendingActions(domain, candidateGroup, dueSoonHours, page, size);
    }

    @PlatformMutation("Request an oversight gate approval")
    @RestPath("/oversight-gates")
    public CommitmentOutcome requestApproval(OversightGateRequest request) {
        return commitmentService.requestApproval(request);
    }
}
