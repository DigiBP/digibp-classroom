package ch.fhnw.digibp.classroom.modeler;

import org.cibseven.modeler.model.FormEntity;
import org.cibseven.modeler.model.ProcessDiagramReduce;
import org.cibseven.modeler.model.UnifiedDiagram;
import org.cibseven.modeler.provider.UnifiedDiagramProvider;
import org.cibseven.modeler.repository.ProcessDiagramRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Primary
@Component
@ConditionalOnProperty(prefix = "cibseven.webclient.modeler", name = "enabled", havingValue = "true")
public class TenantAwareUnifiedDiagramProvider extends UnifiedDiagramProvider {

    private final TenantAwareProcessDiagramProvider diagramProvider;
    private final TenantAwareFormProvider formProvider;

    public TenantAwareUnifiedDiagramProvider(ProcessDiagramRepository repository,
                                             TenantAwareProcessDiagramProvider diagramProvider,
                                             TenantAwareFormProvider formProvider) {
        super(repository);
        this.diagramProvider = diagramProvider;
        this.formProvider = formProvider;
    }

    @Override
    public List<UnifiedDiagram> getDiagrams(String keyword, String type, int firstResult, int maxResults) {
        List<UnifiedDiagram> result = new ArrayList<>();
        if (type == null || type.isBlank() || !"form".equals(type)) {
            diagramProvider.getDiagrams(keyword, null, 0, Integer.MAX_VALUE).stream()
                    .map(DiagramItem::new).forEach(result::add);
        }
        if (type == null || type.isBlank() || "form".equals(type)) {
            formProvider.getForms(keyword, 0, Integer.MAX_VALUE).stream()
                    .map(FormItem::new).forEach(result::add);
        }
        result.sort(Comparator.comparing(UnifiedDiagram::getUpdated,
                Comparator.nullsLast(Comparator.reverseOrder())));
        int from = Math.min(Math.max(firstResult, 0), result.size());
        int to = (int) Math.min((long) from + Math.max(maxResults, 0), result.size());
        return new ArrayList<>(result.subList(from, to));
    }

    private static class DiagramItem implements UnifiedDiagram {
        private final ProcessDiagramReduce diagram;

        DiagramItem(ProcessDiagramReduce diagram) { this.diagram = diagram; }
        public String getId() { return diagram.getId(); }
        public String getName() { return diagram.getName(); }
        public String getType() { return diagram.getType(); }
        public String getProcesskey() { return diagram.getProcesskey(); }
        public String getFormId() { return null; }
        public String getDescription() { return diagram.getDescription(); }
        public LocalDateTime getCreated() { return toLocal(diagram.getCreated()); }
        public LocalDateTime getUpdated() { return toLocal(diagram.getUpdated()); }
        public String getUpdatedBy() { return null; }
        public Integer getVersion() { return diagram.getVersion(); }
    }

    private static class FormItem implements UnifiedDiagram {
        private final FormEntity form;

        FormItem(FormEntity form) { this.form = form; }
        public String getId() { return form.getId(); }
        public String getName() { return form.getFormId(); }
        public String getType() { return "form"; }
        public String getProcesskey() { return form.getFormId(); }
        public String getFormId() { return form.getFormId(); }
        public String getDescription() { return form.getDescription(); }
        public LocalDateTime getCreated() { return toLocal(form.getCreated()); }
        public LocalDateTime getUpdated() { return toLocal(form.getUpdated()); }
        public String getUpdatedBy() { return form.getUpdatedBy(); }
        public Integer getVersion() { return form.getVersion(); }
    }

    private static LocalDateTime toLocal(java.sql.Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
