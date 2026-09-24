package io.casehub.life.app.api;

import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.response.CaseStatisticsResponse;
import io.casehub.life.api.response.SlaComplianceResponse;
import io.casehub.life.api.response.TrustAnalyticsResponse;
import io.casehub.life.app.resource.LifeAnalyticsResource;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.QueryParam;

@McpDomain(value = "life/analytics", app = "life", basePath = "/api/life/analytics", summary = "Life analytics — trends, patterns, outcome tracking")
@ApplicationScoped
public class LifeAnalyticsApi {

    @Inject LifeAnalyticsResource resource;

    @PlatformQuery("Get case statistics")
    @RestPath("/cases")
    public CaseStatisticsResponse caseStatistics(@QueryParam("caseType") String caseType) {
        return resource.caseStatistics(caseType);
    }

    @PlatformQuery("Get SLA compliance metrics")
    @RestPath("/sla")
    public SlaComplianceResponse slaCompliance(@QueryParam("domain") LifeDomain domain) {
        return resource.slaCompliance(domain);
    }

    @PlatformQuery("Get trust analytics")
    @RestPath("/trust")
    public TrustAnalyticsResponse trustAnalytics() {
        return resource.trustAnalytics();
    }
}
