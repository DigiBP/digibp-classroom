/*
 * Copyright (c) 2019. University of Applied Sciences and Arts Northwestern Switzerland FHNW.
 * All rights reserved.
 */

package ch.fhnw.digibp.classroom.rest;

import ch.fhnw.digibp.classroom.service.TenantTaskFilterService;
import org.cibseven.bpm.engine.ProcessEngine;
import org.cibseven.bpm.engine.filter.Filter;
import org.cibseven.bpm.engine.impl.identity.Authentication;
import org.cibseven.bpm.engine.rest.exception.InvalidRequestException;

import jakarta.ws.rs.core.Response;
import java.util.List;

public final class TenantFilterAccess {

    private TenantFilterAccess() {
    }

    public static boolean mayAccess(ProcessEngine engine, Filter filter) {
        Authentication authentication = engine.getIdentityService().getCurrentAuthentication();
        if (authentication == null || authentication.getUserId() == null) {
            return false;
        }

        if (TenantTaskFilterService.isStandard(filter)) {
            return true;
        }

        String filterTenant = TenantTaskFilterService.tenantId(filter);
        return filterTenant != null && tenantIds(authentication).contains(filterTenant);
    }

    public static void assertAccess(ProcessEngine engine, Filter filter) {
        if (!mayAccess(engine, filter)) {
            throw new InvalidRequestException(Response.Status.FORBIDDEN,
                    "Filter belongs to a different tenant");
        }
    }

    public static String requiredTenantId(ProcessEngine engine) {
        Authentication authentication = engine.getIdentityService().getCurrentAuthentication();
        if (authentication == null || authentication.getUserId() == null) {
            throw new InvalidRequestException(Response.Status.FORBIDDEN,
                    "An authenticated tenant user is required");
        }
        List<String> tenantIds = tenantIds(authentication);
        if (tenantIds.size() != 1) {
            throw new InvalidRequestException(Response.Status.BAD_REQUEST,
                    "A task filter must be created in exactly one tenant");
        }
        return tenantIds.get(0);
    }

    public static String currentUserId(ProcessEngine engine) {
        Authentication authentication = engine.getIdentityService().getCurrentAuthentication();
        return authentication == null ? null : authentication.getUserId();
    }

    private static List<String> tenantIds(Authentication authentication) {
        return authentication.getTenantIds() == null ? List.of() : authentication.getTenantIds();
    }
}
