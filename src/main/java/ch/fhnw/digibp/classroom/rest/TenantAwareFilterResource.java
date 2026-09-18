/*
 * Copyright (c) 2019. University of Applied Sciences and Arts Northwestern Switzerland FHNW.
 * All rights reserved.
 */

package ch.fhnw.digibp.classroom.rest;

import ch.fhnw.digibp.classroom.service.TenantTaskFilterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cibseven.bpm.engine.exception.NotValidException;
import org.cibseven.bpm.engine.filter.Filter;
import org.cibseven.bpm.engine.rest.dto.runtime.FilterDto;
import org.cibseven.bpm.engine.rest.exception.InvalidRequestException;
import org.cibseven.bpm.engine.rest.sub.runtime.impl.FilterResourceImpl;

import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

public class TenantAwareFilterResource extends FilterResourceImpl {

    public TenantAwareFilterResource(String processEngineName, ObjectMapper objectMapper,
                                     String filterId, String relativeRootResourcePath) {
        super(processEngineName, objectMapper, filterId, relativeRootResourcePath);
    }

    @Override
    protected Filter getDbFilter() {
        Filter filter = super.getDbFilter();
        TenantFilterAccess.assertAccess(getProcessEngine(), filter);
        return filter;
    }

    @Override
    public void deleteFilter() {
        Filter filter = getDbFilter();
        if (TenantTaskFilterService.isStandard(filter)) {
            throw new InvalidRequestException(Response.Status.FORBIDDEN,
                    "Standard task filters cannot be deleted");
        }
        super.deleteFilter();
    }

    @Override
    public void updateFilter(FilterDto filterDto) {
        Filter filter = getDbFilter();
        if (TenantTaskFilterService.isStandard(filter)) {
            throw new InvalidRequestException(Response.Status.FORBIDDEN,
                    "Standard task filters cannot be changed");
        }

        String tenantId = TenantTaskFilterService.tenantId(filter);
        if (tenantId == null && !TenantFilterAccess.isAdmin(getProcessEngine())) {
            tenantId = TenantFilterAccess.requiredTenantId(getProcessEngine());
        }
        String owner = filter.getOwner();
        try {
            filterDto.updateFilter(filter, getProcessEngine());
        } catch (NotValidException exception) {
            throw new InvalidRequestException(Response.Status.BAD_REQUEST, exception,
                    "Unable to update filter with invalid content");
        }

        Map<String, Object> properties = filter.getProperties() == null
                ? new HashMap<>() : new HashMap<>(filter.getProperties());
        if (tenantId != null) {
            properties.put(TenantTaskFilterService.TENANT_PROPERTY, tenantId);
        }
        properties.remove(TenantTaskFilterService.STANDARD_PROPERTY);
        filter.setProperties(properties);
        filter.setOwner(owner);
        filterService.saveFilter(filter);
    }
}
