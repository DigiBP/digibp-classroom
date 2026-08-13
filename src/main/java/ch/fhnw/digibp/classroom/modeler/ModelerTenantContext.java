package ch.fhnw.digibp.classroom.modeler;

import jakarta.servlet.http.HttpServletRequest;
import org.cibseven.bpm.engine.IdentityService;
import org.cibseven.bpm.engine.identity.Tenant;
import org.cibseven.webapp.auth.BaseUserProvider;
import org.cibseven.webapp.auth.CIBUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "cibseven.webclient.modeler", name = "enabled", havingValue = "true")
public class ModelerTenantContext {

    private static final String ADMIN_GROUP = "camunda-admin";
    private static final String PREFIX = "tenant-";

    private final ObjectProvider<HttpServletRequest> requestProvider;
    private final BaseUserProvider<?> userProvider;
    private final IdentityService identityService;

    public ModelerTenantContext(ObjectProvider<HttpServletRequest> requestProvider,
                                BaseUserProvider<?> userProvider,
                                IdentityService identityService) {
        this.requestProvider = requestProvider;
        this.userProvider = userProvider;
        this.identityService = identityService;
    }

    /** Returns {@code null} for the platform administrator, who may see all tenants. */
    public String currentTenantId() {
        HttpServletRequest request = requestProvider.getIfAvailable();
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        CIBUser user = userProvider.checkAuthorization(request, true);
        if (identityService.createGroupQuery().groupId(ADMIN_GROUP).groupMember(user.getUserID()).count() > 0) {
            return null;
        }

        List<Tenant> tenants = identityService.createTenantQuery().userMember(user.getUserID()).list();
        if (tenants.size() != 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Modeler users must belong to exactly one tenant");
        }
        return tenants.get(0).getId();
    }

    public String qualify(String tenantId, String artifactId) {
        if (tenantId == null) {
            return artifactId;
        }
        String qualified = tenantPrefix(tenantId) + artifactId;
        if (qualified.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Artifact id is too long for tenant-aware Modeler storage: " + artifactId);
        }
        return qualified;
    }

    public String tenantPrefix(String tenantId) {
        return PREFIX + sha256(tenantId).substring(0, 16) + "-";
    }

    public boolean belongsToTenant(String storedId, String tenantId) {
        return tenantId == null || (storedId != null && storedId.startsWith(tenantPrefix(tenantId)));
    }

    public String unqualify(String storedId, String tenantId) {
        if (storedId == null || tenantId == null) {
            return storedId;
        }
        String prefix = tenantPrefix(tenantId);
        return storedId.startsWith(prefix) ? storedId.substring(prefix.length()) : storedId;
    }

    public String unqualify(String storedId) {
        return storedId == null ? null : storedId.replaceFirst("^tenant-[0-9a-f]{16}-", "");
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
