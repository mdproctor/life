package io.casehub.life.app.resource;

import io.casehub.life.api.HouseholdGroups;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.response.CaseStatisticsResponse;
import io.casehub.life.api.response.SlaComplianceResponse;
import io.casehub.life.api.response.TrustAnalyticsResponse;
import io.casehub.life.app.service.LifeAnalyticsService;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Blocking
@ApplicationScoped
@Path("/analytics")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LifeAnalyticsResource {

    @Inject
    LifeAnalyticsService service;

    @GET
    @Path("/cases")
    @RolesAllowed({HouseholdGroups.ADMIN, HouseholdGroups.MEMBER})
    public CaseStatisticsResponse caseStatistics(@QueryParam("caseType") final String caseType) {
        return service.caseStatistics(caseType);
    }

    @GET
    @Path("/sla")
    @RolesAllowed({HouseholdGroups.ADMIN, HouseholdGroups.MEMBER})
    public SlaComplianceResponse slaCompliance(@QueryParam("domain") final LifeDomain domain) {
        return service.slaCompliance(domain);
    }

    @GET
    @Path("/trust")
    @RolesAllowed({HouseholdGroups.ADMIN, HouseholdGroups.MEMBER})
    public TrustAnalyticsResponse trustAnalytics() {
        return service.trustAnalytics();
    }
}
