/*
 * Copyright (c) 2019. University of Applied Sciences and Arts Northwestern Switzerland FHNW.
 * All rights reserved.
 */

package ch.fhnw.digibp.classroom.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.cibseven.bpm.engine.IdentityService;
import org.cibseven.bpm.engine.identity.Tenant;
import org.cibseven.bpm.engine.impl.identity.Authentication;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Restricts identity-management requests made by engineers to their own tenant.
 * The process-engine authorization model itself has no tenant dimension for users,
 * groups and authorizations, so granting ALL on these resources is not sufficient.
 */
public class TenantAdminSecurityFilter implements Filter {

    private static final String ENGINEER_GROUP = "engineer";
    private static final String ADMIN_GROUP = "camunda-admin";

    private final IdentityService identityService;

    public TenantAdminSecurityFilter(IdentityService identityService) {
        this.identityService = identityService;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        Authentication authentication = identityService.getCurrentAuthentication();

        if (authentication == null || authentication.getUserId() == null
                || authentication.getGroupIds().contains(ADMIN_GROUP)
                || !authentication.getGroupIds().contains(ENGINEER_GROUP)) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = engineRestPath(request);
        if (!isIdentityManagementPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        List<Tenant> tenants = identityService.createTenantQuery()
                .userMember(authentication.getUserId()).list();
        if (tenants.size() != 1) {
            deny(response, "Engineer must belong to exactly one tenant");
            return;
        }

        String tenantId = tenants.get(0).getId();
        HttpServletRequest securedRequest = secureRequest(request, response, path,
                authentication.getUserId(), tenantId);
        if (securedRequest != null) {
            filterChain.doFilter(securedRequest, response);
        }
    }

    private HttpServletRequest secureRequest(HttpServletRequest request, HttpServletResponse response,
                                             String path, String userId, String tenantId) throws IOException {
        String method = request.getMethod();

        if (("/user".equals(path) || "/user/count".equals(path)) && "GET".equals(method)) {
            return withParameter(request, "memberOfTenant", tenantId);
        }
        if ("/user/create".equals(path) && "POST".equals(method)) {
            deny(response, "Create tenant users through the Classroom API");
            return null;
        }
        if (path.startsWith("/user/")) {
            String targetUser = firstPathSegment(path.substring("/user/".length()));
            if (!isUserMemberOfTenant(targetUser, tenantId)) {
                deny(response, "User is outside the engineer tenant");
                return null;
            }
            return request;
        }

        if (("/group".equals(path) || "/group/count".equals(path)) && "GET".equals(method)) {
            return withParameter(request, "memberOfTenant", tenantId);
        }
        if ("/group/create".equals(path)) {
            deny(response, "Groups are shared globally and cannot be managed by a tenant engineer");
            return null;
        }
        if (path.startsWith("/group/")) {
            String groupId = firstPathSegment(path.substring("/group/".length()));
            if (!isGroupMemberOfTenant(groupId, tenantId)) {
                deny(response, "Group is outside the engineer tenant");
                return null;
            }
            if (path.matches("/group/[^/]+") && !"GET".equals(method)) {
                deny(response, "Groups are shared globally and cannot be managed by a tenant engineer");
                return null;
            }
            if (path.matches("/group/[^/]+/members/[^/]+")) {
                String targetUser = decode(path.substring(path.lastIndexOf('/') + 1));
                if (!isUserMemberOfTenant(targetUser, tenantId)) {
                    deny(response, "Group membership is outside the engineer tenant");
                    return null;
                }
            }
            return request;
        }

        if (path.startsWith("/authorization") && !"GET".equals(method)) {
            deny(response, "Authorizations are global and cannot be managed by a tenant engineer");
            return null;
        }

        if (("/tenant".equals(path) || "/tenant/count".equals(path)) && "GET".equals(method)) {
            return withParameter(request, "userMember", userId);
        }
        if (("/tenant".equals(path) || "/tenant/create".equals(path)) && "POST".equals(method)) {
            deny(response, "A tenant engineer cannot create tenants");
            return null;
        }
        if (path.startsWith("/tenant/")) {
            String targetTenant = firstPathSegment(path.substring("/tenant/".length()));
            if (!tenantId.equals(targetTenant)) {
                deny(response, "Tenant is outside the engineer tenant");
                return null;
            }
            if (path.matches("/tenant/[^/]+/user-members/[^/]+")) {
                String targetUser = decode(path.substring(path.lastIndexOf('/') + 1));
                if ("PUT".equals(method) && belongsToAnotherTenant(targetUser, tenantId)) {
                    deny(response, "User already belongs to another tenant");
                    return null;
                }
            }
        }

        return request;
    }

    private boolean isIdentityManagementPath(String path) {
        return path.equals("/user") || path.startsWith("/user/")
                || path.equals("/group") || path.startsWith("/group/")
                || path.equals("/authorization") || path.startsWith("/authorization/")
                || path.equals("/tenant") || path.startsWith("/tenant/");
    }

    private boolean isUserMemberOfTenant(String userId, String tenantId) {
        return identityService.createUserQuery().userId(userId).memberOfTenant(tenantId).count() == 1;
    }

    private boolean isGroupMemberOfTenant(String groupId, String tenantId) {
        return identityService.createGroupQuery().groupId(groupId).memberOfTenant(tenantId).count() == 1;
    }

    private boolean belongsToAnotherTenant(String userId, String ownTenantId) {
        return identityService.createTenantQuery().userMember(userId).list().stream()
                .anyMatch(tenant -> !ownTenantId.equals(tenant.getId()));
    }

    private String engineRestPath(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        path = path.replaceFirst("^/engine-rest", "");
        return path.replaceFirst("^/engine/[^/]+", "");
    }

    private String firstPathSegment(String value) {
        int slash = value.indexOf('/');
        return decode(slash < 0 ? value : value.substring(0, slash));
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private HttpServletRequest withParameter(HttpServletRequest request, String name, String value) {
        return new ParameterRequestWrapper(request, name, value);
    }

    private void deny(HttpServletResponse response, String message) throws IOException {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, message);
    }

    private static class ParameterRequestWrapper extends HttpServletRequestWrapper {

        private final Map<String, String[]> parameters;

        ParameterRequestWrapper(HttpServletRequest request, String name, String value) {
            super(request);
            parameters = new LinkedHashMap<>(request.getParameterMap());
            parameters.put(name, new String[]{value});
        }

        @Override
        public String getParameter(String name) {
            String[] values = parameters.get(name);
            return values == null || values.length == 0 ? null : values[0];
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return Collections.unmodifiableMap(parameters);
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(parameters.keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = parameters.get(name);
            return values == null ? null : values.clone();
        }

        @Override
        public String getQueryString() {
            StringBuilder query = new StringBuilder();
            parameters.forEach((name, values) -> {
                for (String value : values) {
                    if (!query.isEmpty()) {
                        query.append('&');
                    }
                    query.append(URLEncoder.encode(name, StandardCharsets.UTF_8));
                    query.append('=');
                    query.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
                }
            });
            return query.toString();
        }
    }
}
