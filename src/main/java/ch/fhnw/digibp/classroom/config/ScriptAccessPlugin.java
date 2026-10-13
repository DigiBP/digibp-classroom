package ch.fhnw.digibp.classroom.config;

import org.cibseven.bpm.dmn.engine.DmnEngineConfiguration;
import org.cibseven.bpm.dmn.engine.impl.DefaultDmnEngineConfiguration;
import org.cibseven.bpm.engine.ProcessEngineException;
import org.cibseven.bpm.engine.delegate.DelegateCaseExecution;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.VariableScope;
import org.cibseven.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.cibseven.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.cibseven.bpm.engine.impl.context.Context;
import org.cibseven.bpm.engine.impl.core.variable.scope.AbstractVariableScope;
import org.cibseven.bpm.engine.impl.dmn.invocation.DecisionInvocation;
import org.cibseven.bpm.engine.impl.identity.Authentication;
import org.cibseven.bpm.engine.impl.interceptor.DelegateInterceptor;
import org.cibseven.bpm.engine.impl.persistence.deploy.Deployer;
import org.cibseven.bpm.engine.impl.persistence.entity.TaskEntity;
import org.cibseven.bpm.engine.impl.scripting.ExecutableScript;
import org.cibseven.bpm.engine.impl.scripting.env.ScriptingEnvironment;
import org.cibseven.bpm.engine.repository.DecisionDefinition;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import java.util.ArrayList;

/** Runtime permission for engine-managed scripts; this is not a Java sandbox. */
public class ScriptAccessPlugin extends AbstractProcessEnginePlugin {
    public static final String SCRIPT_GROUP = "scripting";
    public static final String GROUP_TYPE = "SYSTEM";
    public static final String ADMIN_GROUP = "camunda-admin";

    // DMN scripts have no VariableScope. Keep the actual definition, including a null tenant.
    private final ThreadLocal<DecisionDefinition> decision = new ThreadLocal<>();

    @Override
    public void preInit(ProcessEngineConfigurationImpl configuration) {
        var deployers = configuration.getCustomPreDeployers() == null
                ? new ArrayList<Deployer>()
                : new ArrayList<>(configuration.getCustomPreDeployers());
        deployers.add(0, new ClassReferenceDeployer(this::hasScriptAccess));
        configuration.setCustomPreDeployers(deployers);

        var dmnConfiguration = configuration.getDmnEngineConfiguration();
        if (dmnConfiguration == null) {
            dmnConfiguration = (DefaultDmnEngineConfiguration) DmnEngineConfiguration.createDefaultDmnEngineConfiguration();
            configuration.setDmnEngineConfiguration(dmnConfiguration);
        }
        var resolver = dmnConfiguration.getScriptEngineResolver();
        // DMN captures its resolver during initialization; installing it in postInit is too late.
        dmnConfiguration.setScriptEngineResolver(language -> {
            DecisionDefinition definition = decision.get();
            checkAccess(definition == null ? null : definition.getTenantId());
            // Resolve on every evaluation, including evaluations of cached compiled scripts.
            // The default scripting engines are only available after process-engine initialization.
            return (resolver == null ? configuration.getScriptingEngines() : resolver)
                    .getScriptEngineForLanguage(language);
        });
    }

    @Override
    public void postInit(ProcessEngineConfigurationImpl configuration) {
        ScriptEngine juelEngine = new RestrictedJuelScriptEngine();
        configuration.setScriptingEnvironment(new ScriptingEnvironment(configuration.getScriptFactory(),
                configuration.getEnvScriptResolvers(), configuration.getScriptingEngines()) {
            @Override
            public Object execute(ExecutableScript script, VariableScope scope,
                                  Bindings bindings, ScriptEngine scriptEngine) {
                if ("juel".equalsIgnoreCase(script.getLanguage())) {
                    if (!hasScriptAccess(tenantId(scope))) {
                        scriptEngine = juelEngine;
                    }
                } else {
                    checkAccess(tenantId(scope));
                }
                // Check before environment scripts, dynamic sources and cached scripts execute.
                return super.execute(script, scope, bindings, scriptEngine);
            }
        });

        if (configuration.getDmnEngineConfiguration() != null) {
            DelegateInterceptor delegate = configuration.getDelegateInterceptor();
            configuration.setDelegateInterceptor(invocation -> {
                if (!(invocation instanceof DecisionInvocation decisionInvocation)) {
                    delegate.handleInvocation(invocation);
                    return;
                }
                DecisionDefinition previous = decision.get();
                decision.set(decisionInvocation.getDecisionDefinition());
                try {
                    delegate.handleInvocation(invocation);
                } finally {
                    if (previous == null) {
                        decision.remove();
                    } else {
                        decision.set(previous);
                    }
                }
            });
        }
    }

    private String tenantId(VariableScope scope) {
        if (scope instanceof DelegateExecution execution) {
            return execution.getTenantId();
        }
        if (scope instanceof DelegateCaseExecution execution) {
            return execution.getTenantId();
        }
        if (scope instanceof TaskEntity task) {
            return task.getTenantId();
        }
        if (scope instanceof AbstractVariableScope variables && variables.getParentVariableScope() != null) {
            // Connector mappings wrap the actual execution in a child variable scope.
            return tenantId(variables.getParentVariableScope());
        }
        return null;
    }

    private void checkAccess(String tenantId) {
        if (!hasScriptAccess(tenantId)) {
            throw new ProcessEngineException("Script execution requires SYSTEM group '" + SCRIPT_GROUP
                    + "' assigned to the executing tenant, or authenticated membership in '" + ADMIN_GROUP + "'.");
        }
    }

    private boolean hasScriptAccess(String tenantId) {
        var identityService = Context.getProcessEngineConfiguration().getIdentityService();
        Authentication authentication = identityService.getCurrentAuthentication();
        if (authentication != null && authentication.getUserId() != null
                && authentication.getGroupIds() != null
                && authentication.getGroupIds().contains(ADMIN_GROUP)) {
            return true;
        }
        return tenantId != null && !tenantId.isBlank()
                && identityService.createGroupQuery().groupId(SCRIPT_GROUP).groupType(GROUP_TYPE)
                .memberOfTenant(tenantId).count() == 1;
    }
}
