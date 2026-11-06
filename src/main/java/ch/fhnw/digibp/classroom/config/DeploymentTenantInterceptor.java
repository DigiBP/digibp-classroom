package ch.fhnw.digibp.classroom.config;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;
import org.cibseven.bpm.engine.IdentityService;
import org.cibseven.bpm.engine.ProcessEngine;
import org.cibseven.bpm.engine.impl.identity.Authentication;
import org.cibseven.bpm.engine.rest.exception.InvalidRequestException;
import org.cibseven.bpm.engine.rest.mapper.MultipartFormData;
import org.cibseven.bpm.engine.rest.mapper.MultipartFormData.FormPart;
import org.cibseven.bpm.engine.rest.util.EngineUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Enforces classroom tenant selection without replacing CIB's versioned REST implementation. */
public class DeploymentTenantInterceptor implements ReaderInterceptor {

    private static final String TENANT_ID = "tenant-id";

    @Context
    private UriInfo uriInfo;

    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException {
        Object entity = context.proceed();
        if (!(entity instanceof MultipartFormData payload)) {
            throw new InvalidRequestException(Status.BAD_REQUEST, "Expected multipart deployment data.");
        }

        // Named engine routes use /engine/{name}; a missing name selects the default engine.
        ProcessEngine engine = EngineUtil.lookupProcessEngine(uriInfo.getPathParameters().getFirst("name"));
        if (engine == null) {
            throw new InvalidRequestException(Status.BAD_REQUEST, "No process engine available");
        }
        IdentityService identityService = engine.getIdentityService();
        Authentication authentication = identityService.getCurrentAuthentication();
        FormPart tenantPart = payload.getNamedPart(TENANT_ID);
        String tenantId = tenantPart == null ? null : tenantPart.getTextContent();
        if (authentication != null && authentication.getGroupIds() != null
                && authentication.getGroupIds().contains("camunda-admin")) {
            if (tenantId == null || tenantId.isBlank()) {
                payload.getPartNames().remove(TENANT_ID);
            }
            return payload;
        }

        if (tenantPart != null && tenantId == null) {
            throw new InvalidRequestException(Status.BAD_REQUEST, "Tenant id must be a text field.");
        }
        List<String> tenantIds = authentication == null ? null : authentication.getTenantIds();
        if (tenantId == null || tenantId.isBlank()) {
            if (tenantIds == null || tenantIds.size() != 1) {
                throw new InvalidRequestException(Status.NOT_ACCEPTABLE,
                        "No tenant id provided in the deployment and the authenticated user does not belong to exactly one tenant.");
            }
            payload.addPart(new TenantFormPart(tenantIds.get(0)));
        } else {
            if (identityService.createTenantQuery().tenantId(tenantId).count() == 0) {
                throw new InvalidRequestException(Status.NOT_ACCEPTABLE, "Tenant id provided does not exist.");
            }
            if (tenantIds == null || !tenantIds.contains(tenantId)) {
                throw new InvalidRequestException(Status.FORBIDDEN,
                        "The authenticated user is not a member of the tenant provided in the deployment.");
            }
        }
        return payload;
    }

    private static class TenantFormPart extends FormPart {
        private TenantFormPart(String tenantId) {
            fieldName = TENANT_ID;
            contentType = MediaType.TEXT_PLAIN;
            textContent = tenantId;
            binaryContent = tenantId.getBytes(StandardCharsets.UTF_8);
        }
    }
}
