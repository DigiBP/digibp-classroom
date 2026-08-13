package ch.fhnw.digibp.classroom.modeler;

import org.cibseven.modeler.model.DiagramUsageEntity;
import org.cibseven.modeler.model.FormUsageEntity;
import org.cibseven.modeler.provider.DiagramUsageProvider;
import org.cibseven.modeler.provider.FormUsageProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Primary
@Component
@ConditionalOnProperty(prefix = "cibseven.webclient.modeler", name = "enabled", havingValue = "true")
class TenantAwareDiagramUsageProvider extends DiagramUsageProvider {

    private final TenantAwareProcessDiagramProvider diagramProvider;

    TenantAwareDiagramUsageProvider(TenantAwareProcessDiagramProvider diagramProvider) {
        this.diagramProvider = diagramProvider;
    }

    @Override
    public DiagramUsageEntity checkSessionUser(String diagramId) {
        diagramProvider.findById(diagramId).orElseThrow();
        return super.checkSessionUser(diagramId);
    }

    @Override
    public DiagramUsageEntity findBySessionId(String sessionId) {
        DiagramUsageEntity usage = super.findBySessionId(sessionId);
        if (usage != null && usage.getDiagram() != null) {
            diagramProvider.findById(usage.getDiagram().getId()).orElseThrow();
        }
        return usage;
    }

    @Override
    public Optional<DiagramUsageEntity> getSessionById(String id) {
        Optional<DiagramUsageEntity> usage = super.getSessionById(id);
        usage.filter(item -> item.getDiagram() != null)
                .ifPresent(item -> diagramProvider.findById(item.getDiagram().getId()).orElseThrow());
        return usage;
    }
}

@Primary
@Component
@ConditionalOnProperty(prefix = "cibseven.webclient.modeler", name = "enabled", havingValue = "true")
class TenantAwareFormUsageProvider extends FormUsageProvider {

    private final TenantAwareFormProvider formProvider;

    TenantAwareFormUsageProvider(TenantAwareFormProvider formProvider) {
        this.formProvider = formProvider;
    }

    @Override
    public FormUsageEntity checkSessionUser(String formId) {
        formProvider.findById(formId).orElseThrow();
        return super.checkSessionUser(formId);
    }

    @Override
    public FormUsageEntity findBySessionId(String sessionId) {
        FormUsageEntity usage = super.findBySessionId(sessionId);
        if (usage != null && usage.getForm() != null) {
            formProvider.findById(usage.getForm().getId()).orElseThrow();
        }
        return usage;
    }

    @Override
    public Optional<FormUsageEntity> getSessionById(String id) {
        Optional<FormUsageEntity> usage = super.getSessionById(id);
        usage.filter(item -> item.getForm() != null)
                .ifPresent(item -> formProvider.findById(item.getForm().getId()).orElseThrow());
        return usage;
    }
}
