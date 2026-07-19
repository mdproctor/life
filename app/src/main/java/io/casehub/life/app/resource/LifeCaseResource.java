/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.life.app.resource;

import io.casehub.life.api.HouseholdGroups;
import io.casehub.life.api.LifeCaseStatus;
import io.casehub.life.api.LifeCaseType;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.request.CreateLifeCaseRequest;
import io.casehub.life.api.response.LifeCaseResponse;
import io.casehub.life.api.response.PagedResponse;
import io.casehub.life.app.engine.LifeCaseService;
import io.casehub.life.app.service.LifeCaseQueryService;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Blocking
@ApplicationScoped
@Path("/life-cases")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LifeCaseResource {

    @Inject
    LifeCaseService lifeCaseService;

    @Inject
    LifeCaseQueryService queryService;

    @POST
    @RolesAllowed({HouseholdGroups.ADMIN, HouseholdGroups.MEMBER})
    public Response create(CreateLifeCaseRequest request) {
        LifeCaseResponse response = lifeCaseService.startCase(request);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @GET
    @RolesAllowed({HouseholdGroups.ADMIN, HouseholdGroups.MEMBER, HouseholdGroups.JUNIOR})
    public PagedResponse<LifeCaseResponse> list(
            @QueryParam("domain") LifeDomain domain,
            @QueryParam("status") LifeCaseStatus status,
            @QueryParam("caseType") LifeCaseType caseType,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return queryService.listCases(domain, status, caseType, page, size);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({HouseholdGroups.ADMIN, HouseholdGroups.MEMBER, HouseholdGroups.JUNIOR})
    public Response findById(@PathParam("id") UUID id) {
        return queryService.findById(id)
                           .map(r -> Response.ok(r).build())
                           .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }
}
