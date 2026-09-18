/*
 * Copyright (c) 2019. University of Applied Sciences and Arts Northwestern Switzerland FHNW.
 * All rights reserved.
 */

package ch.fhnw.digibp.classroom.service;

import org.cibseven.bpm.engine.AuthorizationService;
import org.cibseven.bpm.engine.FilterService;
import org.cibseven.bpm.engine.IdentityService;
import org.cibseven.bpm.engine.TaskService;
import org.cibseven.bpm.engine.authorization.Authorization;
import org.cibseven.bpm.engine.filter.Filter;
import org.cibseven.bpm.engine.identity.Tenant;
import org.cibseven.bpm.engine.identity.User;
import org.cibseven.bpm.engine.task.TaskQuery;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.cibseven.bpm.engine.authorization.Permissions.READ;
import static org.cibseven.bpm.engine.authorization.Resources.FILTER;

@Service
public class TenantTaskFilterService {

    public static final String TENANT_PROPERTY = "digibpTenantId";
    public static final String STANDARD_PROPERTY = "digibpStandardFilter";
    public static final String ALL_TASKS = "All Tasks";
    public static final String MY_TASKS = "My Tasks";
    public static final String ROLE_GROUP_TASKS = "Role/Group Tasks";

    private static final Set<String> LEGACY_STANDARD_NAMES = Set.of(
            ALL_TASKS, MY_TASKS, ROLE_GROUP_TASKS, "Role/Groupe Tasks");

    private final FilterService filterService;
    private final TaskService taskService;
    private final IdentityService identityService;
    private final AuthorizationService authorizationService;

    public TenantTaskFilterService(FilterService filterService, TaskService taskService,
                                   IdentityService identityService,
                                   AuthorizationService authorizationService) {
        this.filterService = filterService;
        this.taskService = taskService;
        this.identityService = identityService;
        this.authorizationService = authorizationService;
    }

    public void synchronizeAllTenants() {
        removeLegacyGlobalStandardFilters();
        for (Tenant tenant : identityService.createTenantQuery().list()) {
            ensureTenantFilters(tenant.getId());
            for (User user : identityService.createUserQuery().memberOfTenant(tenant.getId()).list()) {
                grantStandardFiltersToUser(tenant.getId(), user.getId());
            }
        }
    }

    public void ensureTenantFilters(String tenantId) {
        ensureFilter(tenantId, ALL_TASKS, "All tasks", 0,
                taskService.createTaskQuery().tenantIdIn(tenantId));
        ensureFilter(tenantId, MY_TASKS, "Tasks assigned to me", 1,
                taskService.createTaskQuery().tenantIdIn(tenantId)
                        .taskAssigneeExpression("${currentUser()}"));
        ensureFilter(tenantId, ROLE_GROUP_TASKS, "Tasks assigned to my groups", 2,
                taskService.createTaskQuery().tenantIdIn(tenantId)
                        .taskCandidateGroupInExpression("${currentUserGroups()}"));
    }

    public void grantStandardFiltersToUser(String tenantId, String userId) {
        for (Filter filter : tenantFilters(tenantId)) {
            Authorization authorization = authorizationService.createAuthorizationQuery()
                    .userIdIn(userId).resourceType(FILTER).resourceId(filter.getId()).singleResult();
            if (authorization == null) {
                authorization = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
                authorization.setUserId(userId);
                authorization.setResource(FILTER);
                authorization.setResourceId(filter.getId());
            }
            authorization.addPermission(READ);
            authorizationService.saveAuthorization(authorization);
        }
    }

    public void deleteTenantFilters(String tenantId) {
        for (Filter filter : tenantFilters(tenantId)) {
            filterService.deleteFilter(filter.getId());
        }
    }

    public List<Filter> tenantFilters(String tenantId) {
        return filterService.createFilterQuery().list().stream()
                .filter(filter -> tenantId.equals(tenantId(filter)))
                .toList();
    }

    public static String tenantId(Filter filter) {
        Object tenantId = filter.getProperties() == null
                ? null : filter.getProperties().get(TENANT_PROPERTY);
        return tenantId instanceof String ? (String) tenantId : null;
    }

    public static boolean isStandard(Filter filter) {
        return filter.getProperties() != null
                && Boolean.TRUE.equals(filter.getProperties().get(STANDARD_PROPERTY));
    }

    private void ensureFilter(String tenantId, String name, String description, int priority,
                              TaskQuery query) {
        Filter existing = tenantFilters(tenantId).stream()
                .filter(TenantTaskFilterService::isStandard)
                .filter(filter -> name.equals(filter.getName()))
                .findFirst().orElse(null);
        if (existing != null) {
            return;
        }

        Map<String, Object> properties = new HashMap<>();
        properties.put("description", description);
        properties.put("priority", priority);
        properties.put("refresh", true);
        properties.put(TENANT_PROPERTY, tenantId);
        properties.put(STANDARD_PROPERTY, true);
        Filter filter = filterService.newTaskFilter()
                .setName(name)
                .setProperties(properties)
                .setQuery(query);
        filterService.saveFilter(filter);
    }

    private void removeLegacyGlobalStandardFilters() {
        for (Filter filter : filterService.createFilterQuery().list()) {
            if (tenantId(filter) == null && filter.getOwner() == null
                    && LEGACY_STANDARD_NAMES.contains(filter.getName())) {
                filterService.deleteFilter(filter.getId());
            }
        }
    }
}
