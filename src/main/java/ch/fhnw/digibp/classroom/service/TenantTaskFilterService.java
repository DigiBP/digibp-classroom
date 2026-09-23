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
import org.cibseven.bpm.engine.task.TaskQuery;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static org.cibseven.bpm.engine.authorization.Permissions.READ;
import static org.cibseven.bpm.engine.authorization.Resources.FILTER;

@Service
public class TenantTaskFilterService {

    private static final Logger LOGGER = Logger.getLogger(TenantTaskFilterService.class.getName());

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

    public void synchronizeSystemFilters() {
        Set<String> systemFilterIds = new HashSet<>();
        systemFilterIds.add(ensureSystemFilter(ALL_TASKS, "All tasks", 0,
                taskService.createTaskQuery()).getId());
        systemFilterIds.add(ensureSystemFilter(MY_TASKS, "Tasks assigned to me", 1,
                taskService.createTaskQuery().taskAssigneeExpression("${currentUser()}")).getId());
        systemFilterIds.add(ensureSystemFilter(ROLE_GROUP_TASKS, "Tasks assigned to my groups", 2,
                taskService.createTaskQuery()
                        .taskCandidateGroupInExpression("${currentUserGroups()}")).getId());
        removeObsoleteSystemFilterCopies(systemFilterIds);
        assignTenantToLegacyFilters();
    }

    /**
     * Kept for callers compiled against the former tenant-specific implementation.
     */
    public void synchronizeAllTenants() {
        synchronizeSystemFilters();
    }

    /**
     * Standard filters are global now; the tenant argument is intentionally ignored.
     */
    public void ensureTenantFilters(String tenantId) {
        synchronizeSystemFilters();
    }

    /**
     * Shared standard filters use global READ grants, except All Tasks, which is granted to engineers.
     */
    public void grantStandardFiltersToUser(String tenantId, String userId) {
        synchronizeSystemFilters();
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
        return tenantId(filter) == null && hasStandardMarker(filter);
    }

    private Filter ensureSystemFilter(String name, String description, int priority, TaskQuery query) {
        Filter filter = filterService.createFilterQuery().list().stream()
                .filter(candidate -> canonicalSystemName(candidate).equals(name))
                .sorted((left, right) -> Boolean.compare(isStandard(right), isStandard(left)))
                .findFirst().orElse(null);
        if (filter == null) {
            filter = filterService.newTaskFilter();
        }

        Map<String, Object> properties = filter.getProperties() == null
                ? new HashMap<>() : new HashMap<>(filter.getProperties());
        properties.put("description", description);
        properties.put("priority", priority);
        properties.put("refresh", true);
        properties.remove(TENANT_PROPERTY);
        properties.put(STANDARD_PROPERTY, true);
        filter.setName(name)
                .setOwner(null)
                .setProperties(properties)
                .setQuery(query);
        filterService.saveFilter(filter);
        if (ALL_TASKS.equals(name)) {
            ensureEngineerReadAuthorization(filter);
        } else {
            ensureGlobalReadAuthorization(filter);
        }
        return filter;
    }

    private void ensureEngineerReadAuthorization(Filter filter) {
        for (Authorization authorization : authorizationService.createAuthorizationQuery()
                .resourceType(FILTER).resourceId(filter.getId()).list()) {
            if (authorization.getAuthorizationType() == Authorization.AUTH_TYPE_GLOBAL) {
                authorizationService.deleteAuthorization(authorization.getId());
            }
        }
        Authorization authorization = authorizationService.createAuthorizationQuery()
                .groupIdIn("engineer").resourceType(FILTER).resourceId(filter.getId()).list().stream()
                .filter(item -> item.getAuthorizationType() == Authorization.AUTH_TYPE_GRANT)
                .findFirst().orElse(null);
        if (authorization == null) {
            authorization = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
            authorization.setGroupId("engineer");
            authorization.setResource(FILTER);
            authorization.setResourceId(filter.getId());
        }
        authorization.addPermission(READ);
        authorizationService.saveAuthorization(authorization);
    }

    private void ensureGlobalReadAuthorization(Filter filter) {
        Authorization authorization = authorizationService.createAuthorizationQuery()
                .resourceType(FILTER).resourceId(filter.getId()).list().stream()
                .filter(item -> item.getAuthorizationType() == Authorization.AUTH_TYPE_GLOBAL)
                .findFirst().orElse(null);
        if (authorization == null) {
            authorization = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_GLOBAL);
            authorization.setResource(FILTER);
            authorization.setResourceId(filter.getId());
        }
        authorization.addPermission(READ);
        authorizationService.saveAuthorization(authorization);
    }

    private void removeObsoleteSystemFilterCopies(Set<String> systemFilterIds) {
        for (Filter filter : filterService.createFilterQuery().list()) {
            if (!systemFilterIds.contains(filter.getId()) && isSystemFilterCandidate(filter)) {
                filterService.deleteFilter(filter.getId());
            }
        }
    }

    private void assignTenantToLegacyFilters() {
        for (Filter filter : filterService.createFilterQuery().list()) {
            if (isStandard(filter) || tenantId(filter) != null || filter.getOwner() == null) {
                continue;
            }
            List<Tenant> ownerTenants = identityService.createTenantQuery()
                    .userMember(filter.getOwner()).list();
            if (ownerTenants.size() != 1) {
                LOGGER.warning(() -> "Legacy task filter '" + filter.getId()
                        + "' cannot be assigned unambiguously to a tenant and remains inaccessible");
                continue;
            }

            Map<String, Object> properties = filter.getProperties() == null
                    ? new HashMap<>() : new HashMap<>(filter.getProperties());
            properties.put(TENANT_PROPERTY, ownerTenants.get(0).getId());
            properties.remove(STANDARD_PROPERTY);
            filter.setProperties(properties);
            filterService.saveFilter(filter);
        }
    }

    private boolean isSystemFilterCandidate(Filter filter) {
        return !canonicalSystemName(filter).isEmpty();
    }

    private static boolean hasStandardMarker(Filter filter) {
        return filter.getProperties() != null
                && Boolean.TRUE.equals(filter.getProperties().get(STANDARD_PROPERTY));
    }

    private static String canonicalSystemName(Filter filter) {
        if (filter.getName() == null) {
            return "";
        }
        if (!hasStandardMarker(filter)
                && (tenantId(filter) != null || filter.getOwner() != null
                || !LEGACY_STANDARD_NAMES.contains(filter.getName()))) {
            return "";
        }
        return "Role/Groupe Tasks".equals(filter.getName()) ? ROLE_GROUP_TASKS : filter.getName();
    }
}
