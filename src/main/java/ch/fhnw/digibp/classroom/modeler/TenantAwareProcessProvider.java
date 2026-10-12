package ch.fhnw.digibp.classroom.modeler;

import jakarta.servlet.http.HttpServletRequest;
import org.cibseven.webapp.auth.CIBUser;
import org.cibseven.webapp.exception.ExpressionEvaluationException;
import org.cibseven.webapp.exception.SystemException;
import org.cibseven.webapp.exception.UnsupportedTypeException;
import org.cibseven.webapp.providers.ProcessProvider;
import org.cibseven.webapp.rest.model.ProcessStart;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Map;

@Primary
@Component
@ConditionalOnProperty(prefix = "cibseven.webclient.modeler", name = "enabled", havingValue = "true")
public class TenantAwareProcessProvider extends ProcessProvider {

    private final ObjectProvider<ModelerTenantContext> tenantContext;
    private final ObjectProvider<HttpServletRequest> requestProvider;

    public TenantAwareProcessProvider(ObjectProvider<ModelerTenantContext> tenantContext,
                                      ObjectProvider<HttpServletRequest> requestProvider) {
        this.tenantContext = tenantContext;
        this.requestProvider = requestProvider;
    }

    @Override
    public ProcessStart startProcess(String key, String tenantId, Map<String, Object> data, CIBUser user)
            throws SystemException, UnsupportedTypeException, ExpressionEvaluationException {
        HttpServletRequest request = requestProvider.getIfAvailable();
        if (tenantId == null && request != null
                && request.getRequestURI().contains("/modeler/deployment/start/")) {
            tenantId = tenantContext.getObject().currentTenantId();
        }
        return super.startProcess(key, tenantId, data, user);
    }
}
