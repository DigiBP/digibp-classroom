/*
 * Copyright (c) 2019. University of Applied Sciences and Arts Northwestern Switzerland FHNW.
 * All rights reserved.
 */

package ch.fhnw.digibp.classroom.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.cibseven.bpm.engine.IdentityService;
import org.cibseven.bpm.engine.identity.Tenant;
import org.cibseven.bpm.engine.impl.identity.Authentication;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Restricts identity-management requests made by engineers to their own tenant.
 * The process-engine authorization model itself has no tenant dimension for users,
 * groups and authorizations, so granting ALL on these resources is not sufficient.
 */
public class TenantAdminSecurityFilter implements Filter {

    private static final String ENGINEER_GROUP = "engineer";
    private static final String ADMIN_GROUP = "camunda-admin";
    private static final Set<String> SHARED_ROLE_GROUPS = Set.of(
            "owner", "manager", "analyst", ENGINEER_GROUP,
            "initiator", "worker", "assistant", "chef", "courier");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        CreationRequest creationRequest = readCreationRequest(request, response, path);
        if (creationRequest != null && creationRequest.id() == null) {
            return;
        }

        HttpServletRequest effectiveRequest = creationRequest == null ? request : creationRequest.request();
        HttpServletRequest securedRequest = secureRequest(effectiveRequest, response, path,
                authentication.getUserId(), tenantId);
        if (securedRequest == null) {
            return;
        }

        if (creationRequest == null) {
            filterChain.doFilter(securedRequest, response);
            return;
        }

        ContentCachingResponseWrapper bufferedResponse = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(securedRequest, bufferedResponse);
        if (isSuccessful(bufferedResponse)) {
            try {
                assignToTenant(creationRequest, tenantId);
            } catch (RuntimeException exception) {
                rollbackCreation(creationRequest, exception);
                bufferedResponse.reset();
                bufferedResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Created identity could not be assigned to the engineer tenant");
            }
        }
        bufferedResponse.copyBodyToResponse();
    }

    private HttpServletRequest secureRequest(HttpServletRequest request, HttpServletResponse response,
                                             String path, String userId, String tenantId) throws IOException {
        String method = request.getMethod();

        if (("/user".equals(path) || "/user/count".equals(path)) && "GET".equals(method)) {
            return withParameter(request, "memberOfTenant", tenantId);
        }
        if ("/user/create".equals(path) && "POST".equals(method)) {
            return request;
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
        if ("/group/create".equals(path) && "POST".equals(method)) {
            return request;
        }
        if (path.startsWith("/group/")) {
            String groupId = firstPathSegment(path.substring("/group/".length()));
            if (!isGroupMemberOfTenant(groupId, tenantId)) {
                deny(response, "Group is outside the engineer tenant");
                return null;
            }
            if (path.matches("/group/[^/]+") && !"GET".equals(method)) {
                if (SHARED_ROLE_GROUPS.contains(groupId) || groupBelongsToAnotherTenant(groupId, tenantId)) {
                    deny(response, "Shared groups cannot be modified by a tenant engineer");
                    return null;
                }
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
            if (request.getParameter("groupMember") != null) {
                return withParameter(request, "id", tenantId);
            }
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
            if ("DELETE".equals(method) && path.matches("/tenant/[^/]+")) {
                deny(response, "A tenant engineer cannot delete their tenant");
                return null;
            }
            if ("DELETE".equals(method)
                    && path.matches("/tenant/[^/]+/(user-members|group-members)/[^/]+")) {
                deny(response, "Tenant memberships cannot be removed by a tenant engineer");
                return null;
            }
            if (path.matches("/tenant/[^/]+/user-members/[^/]+")) {
                String targetUser = decode(path.substring(path.lastIndexOf('/') + 1));
                if ("PUT".equals(method) && belongsToAnotherTenant(targetUser, tenantId)) {
                    deny(response, "User already belongs to another tenant");
                    return null;
                }
            }
            if (path.matches("/tenant/[^/]+/group-members/[^/]+") && "PUT".equals(method)) {
                String groupId = decode(path.substring(path.lastIndexOf('/') + 1));
                if (!isGroupMemberOfTenant(groupId, tenantId)) {
                    deny(response, "Group membership is outside the engineer tenant");
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

    private boolean groupBelongsToAnotherTenant(String groupId, String ownTenantId) {
        return identityService.createTenantQuery().groupMember(groupId).list().stream()
                .anyMatch(tenant -> !ownTenantId.equals(tenant.getId()));
    }

    private CreationRequest readCreationRequest(HttpServletRequest request, HttpServletResponse response,
                                                String path) throws IOException {
        IdentityType type;
        if ("POST".equals(request.getMethod()) && "/user/create".equals(path)) {
            type = IdentityType.USER;
        } else if ("POST".equals(request.getMethod()) && "/group/create".equals(path)) {
            type = IdentityType.GROUP;
        } else {
            return null;
        }

        byte[] body = request.getInputStream().readAllBytes();
        CachedBodyRequestWrapper wrappedRequest = new CachedBodyRequestWrapper(request, body);
        try {
            JsonNode json = OBJECT_MAPPER.readTree(body);
            JsonNode idNode = type == IdentityType.USER ? json.path("profile").path("id") : json.path("id");
            String id = idNode.isTextual() ? idNode.textValue() : null;
            if (id == null || id.isBlank()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Request must provide a valid identity id");
                return new CreationRequest(wrappedRequest, type, null);
            }
            return new CreationRequest(wrappedRequest, type, id);
        } catch (Exception exception) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Request must contain valid JSON");
            return new CreationRequest(wrappedRequest, type, null);
        }
    }

    private boolean isSuccessful(HttpServletResponse response) {
        return response.getStatus() >= 200 && response.getStatus() < 300;
    }

    private void assignToTenant(CreationRequest creationRequest, String tenantId) {
        if (creationRequest.type() == IdentityType.USER) {
            identityService.createTenantUserMembership(tenantId, creationRequest.id());
        } else {
            identityService.createTenantGroupMembership(tenantId, creationRequest.id());
        }
    }

    private void rollbackCreation(CreationRequest creationRequest, RuntimeException assignmentException) {
        try {
            if (creationRequest.type() == IdentityType.USER) {
                identityService.deleteUser(creationRequest.id());
            } else {
                identityService.deleteGroup(creationRequest.id());
            }
        } catch (RuntimeException rollbackException) {
            assignmentException.addSuppressed(rollbackException);
        }
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

    private enum IdentityType {
        USER, GROUP
    }

    private record CreationRequest(HttpServletRequest request, IdentityType type, String id) {
    }

    private static class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body.clone();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // Synchronous requests do not require asynchronous read notifications.
                }

                @Override
                public int read() {
                    return input.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
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
