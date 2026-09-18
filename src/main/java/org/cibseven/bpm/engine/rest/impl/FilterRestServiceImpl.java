/*
 * Copyright (c) 2019. University of Applied Sciences and Arts Northwestern Switzerland FHNW.
 * All rights reserved.
 */

package org.cibseven.bpm.engine.rest.impl;

import ch.fhnw.digibp.classroom.rest.TenantAwareFilterResource;
import ch.fhnw.digibp.classroom.rest.TenantFilterAccess;
import ch.fhnw.digibp.classroom.service.TenantTaskFilterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cibseven.bpm.engine.EntityTypes;
import org.cibseven.bpm.engine.FilterService;
import org.cibseven.bpm.engine.ProcessEngine;
import org.cibseven.bpm.engine.exception.NotValidException;
import org.cibseven.bpm.engine.filter.Filter;
import org.cibseven.bpm.engine.filter.FilterQuery;
import org.cibseven.bpm.engine.rest.FilterRestService;
import org.cibseven.bpm.engine.rest.dto.CountResultDto;
import org.cibseven.bpm.engine.rest.dto.ResourceOptionsDto;
import org.cibseven.bpm.engine.rest.dto.runtime.FilterDto;
import org.cibseven.bpm.engine.rest.dto.runtime.FilterQueryDto;
import org.cibseven.bpm.engine.rest.exception.InvalidRequestException;
import org.cibseven.bpm.engine.rest.sub.runtime.FilterResource;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.cibseven.bpm.engine.authorization.Authorization.ANY;
import static org.cibseven.bpm.engine.authorization.Permissions.CREATE;
import static org.cibseven.bpm.engine.authorization.Resources.FILTER;

/**
 * Tenant-aware replacement for the engine's filter REST resource.
 */
public class FilterRestServiceImpl extends AbstractAuthorizedRestResource implements FilterRestService {

    public FilterRestServiceImpl(String engineName, ObjectMapper objectMapper) {
        super(engineName, FILTER, ANY, objectMapper);
    }

    @Override
    public FilterResource getFilter(String filterId) {
        return new TenantAwareFilterResource(getProcessEngine().getName(), getObjectMapper(), filterId,
                relativeRootResourcePath);
    }

    @Override
    public List<FilterDto> getFilters(UriInfo uriInfo, Boolean itemCount,
                                      Integer firstResult, Integer maxResults) {
        List<Filter> visible = visibleFilters(uriInfo.getQueryParameters());
        int from = firstResult == null ? 0 : Math.max(0, firstResult);
        int to = maxResults == null ? visible.size()
                : Math.min(visible.size(), from + Math.max(0, maxResults));
        if (from >= visible.size()) {
            return List.of();
        }

        FilterService filterService = getProcessEngine().getFilterService();
        List<FilterDto> result = new ArrayList<>();
        for (Filter filter : visible.subList(from, to)) {
            FilterDto dto = FilterDto.fromFilter(filter);
            if (Boolean.TRUE.equals(itemCount)) {
                dto.setItemCount(filterService.count(filter.getId()));
            }
            result.add(dto);
        }
        return result;
    }

    @Override
    public CountResultDto getFiltersCount(UriInfo uriInfo) {
        return new CountResultDto(visibleFilters(uriInfo.getQueryParameters()).size());
    }

    @Override
    public FilterDto createFilter(FilterDto filterDto) {
        ProcessEngine engine = getProcessEngine();
        if (!EntityTypes.TASK.equals(filterDto.getResourceType())) {
            throw new InvalidRequestException(Response.Status.BAD_REQUEST,
                    "Unable to create filter with invalid resource type '" + filterDto.getResourceType() + "'");
        }

        Filter filter = engine.getFilterService().newTaskFilter();
        try {
            filterDto.updateFilter(filter, engine);
        } catch (NotValidException exception) {
            throw new InvalidRequestException(Response.Status.BAD_REQUEST, exception,
                    "Unable to create filter with invalid content");
        }

        if (!TenantFilterAccess.isAdmin(engine)) {
            Map<String, Object> properties = filter.getProperties() == null
                    ? new HashMap<>() : new HashMap<>(filter.getProperties());
            properties.put(TenantTaskFilterService.TENANT_PROPERTY,
                    TenantFilterAccess.requiredTenantId(engine));
            properties.remove(TenantTaskFilterService.STANDARD_PROPERTY);
            filter.setProperties(properties);
            filter.setOwner(TenantFilterAccess.currentUserId(engine));
        }
        engine.getFilterService().saveFilter(filter);
        return FilterDto.fromFilter(filter);
    }

    @Override
    public ResourceOptionsDto availableOperations(UriInfo context) {
        UriBuilder baseUriBuilder = context.getBaseUriBuilder()
                .path(relativeRootResourcePath).path(FilterRestService.PATH);
        ResourceOptionsDto options = new ResourceOptionsDto();
        URI baseUri = baseUriBuilder.build();
        options.addReflexiveLink(baseUri, HttpMethod.GET, "list");
        options.addReflexiveLink(baseUriBuilder.clone().path("/count").build(), HttpMethod.GET, "count");
        if (isAuthorized(CREATE)) {
            options.addReflexiveLink(baseUriBuilder.clone().path("/create").build(), HttpMethod.POST, "create");
        }
        return options;
    }

    private List<Filter> visibleFilters(MultivaluedMap<String, String> queryParameters) {
        ProcessEngine engine = getProcessEngine();
        FilterQuery query = new FilterQueryDto(getObjectMapper(), queryParameters).toQuery(engine);
        return query.list().stream()
                .filter(filter -> TenantFilterAccess.mayAccess(engine, filter))
                .toList();
    }
}
