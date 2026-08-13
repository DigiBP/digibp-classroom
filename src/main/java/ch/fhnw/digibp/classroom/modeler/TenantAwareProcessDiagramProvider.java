package ch.fhnw.digibp.classroom.modeler;

import jakarta.persistence.EntityNotFoundException;
import org.cibseven.modeler.model.ProcessDiagramEntity;
import org.cibseven.modeler.model.ProcessDiagramReduce;
import org.cibseven.modeler.provider.DBProcessDiagramProvider;
import org.cibseven.modeler.repository.ProcessDiagramRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Primary
@Component
@ConditionalOnProperty(prefix = "cibseven.webclient.modeler", name = "enabled", havingValue = "true")
public class TenantAwareProcessDiagramProvider extends DBProcessDiagramProvider {

    private final ProcessDiagramRepository repository;
    private final ModelerTenantContext tenantContext;
    private final ModelerDeploymentSynchronizer synchronizer;

    public TenantAwareProcessDiagramProvider(ProcessDiagramRepository repository,
                                             ModelerTenantContext tenantContext,
                                             ModelerDeploymentSynchronizer synchronizer) {
        this.repository = repository;
        this.tenantContext = tenantContext;
        this.synchronizer = synchronizer;
    }

    @Override
    public List<ProcessDiagramReduce> getDiagrams(String keyword, String diagramType,
                                                   int firstResult, int maxResults) {
        String tenantId = tenantContext.currentTenantId();
        synchronizer.synchronize(tenantId);
        String normalizedKeyword = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        String normalizedType = diagramType == null ? "" : diagramType;

        List<ProcessDiagramReduce> result = repository.findAll(Sort.by("updated").descending()).stream()
                .filter(entity -> tenantContext.belongsToTenant(entity.getProcesskey(), tenantId))
                .filter(entity -> normalizedType.isEmpty() || entity.getType().contains(normalizedType))
                .filter(entity -> normalizedKeyword.isEmpty()
                        || value(entity.getName()).contains(normalizedKeyword)
                        || value(actualKey(entity)).contains(normalizedKeyword))
                .map(entity -> (ProcessDiagramReduce) new DiagramView(entity, actualKey(entity)))
                .toList();
        return page(result, firstResult, maxResults);
    }

    @Override
    public List<ProcessDiagramReduce> getDiagrams(int firstResult, int maxResults) {
        return getDiagrams(null, null, firstResult, maxResults);
    }

    @Override
    public Optional<ProcessDiagramEntity> findById(String id) {
        String tenantId = tenantContext.currentTenantId();
        return repository.findById(id).map(entity -> copy(assertAccess(entity, tenantId)));
    }

    @Override
    public ProcessDiagramEntity findByName(String name) {
        String tenantId = tenantContext.currentTenantId();
        return repository.findAll().stream()
                .filter(entity -> tenantContext.belongsToTenant(entity.getProcesskey(), tenantId))
                .filter(entity -> name.equals(entity.getName()))
                .findFirst().map(this::copy).orElse(null);
    }

    @Override
    public ProcessDiagramEntity findByProcessKey(String key) {
        String tenantId = tenantContext.currentTenantId();
        ProcessDiagramEntity entity;
        if (tenantId == null) {
            entity = repository.findAll().stream()
                    .filter(item -> key.equals(actualKey(item))).findFirst().orElse(null);
        } else {
            entity = repository.findByProcesskey(tenantContext.qualify(tenantId, key));
        }
        return entity == null ? null : copy(entity);
    }

    @Override
    public ProcessDiagramEntity createDiagram(ProcessDiagramEntity entity) {
        String tenantId = tenantContext.currentTenantId();
        entity.setProcesskey(tenantContext.qualify(tenantId, entity.getProcesskey()));
        entity.setCreated(Timestamp.valueOf(LocalDateTime.now()));
        entity.setUpdated(Timestamp.valueOf(LocalDateTime.now()));
        return copy(repository.save(entity));
    }

    @Override
    public ProcessDiagramEntity updateDiagram(ProcessDiagramEntity entity) {
        String tenantId = tenantContext.currentTenantId();
        ProcessDiagramEntity existing = repository.findById(entity.getId())
                .map(item -> assertAccess(item, tenantId))
                .orElseThrow(() -> new EntityNotFoundException("ProcessDiagramEntity not found"));
        existing.setName(entity.getName());
        existing.setProcesskey(qualifiedUpdatedKey(existing, entity.getProcesskey(), tenantId));
        existing.setDescription(entity.getDescription());
        existing.setType(entity.getType());
        existing.setDiagram(entity.getDiagram());
        existing.setActive(entity.getActive());
        existing.setUpdated(Timestamp.valueOf(LocalDateTime.now()));
        existing.setUpdatedBy(entity.getUpdatedBy());
        return copy(repository.save(existing));
    }

    @Transactional
    @Override
    public void delete(String id) {
        String tenantId = tenantContext.currentTenantId();
        ProcessDiagramEntity entity = repository.findById(id)
                .map(item -> assertAccess(item, tenantId))
                .orElseThrow(() -> new EntityNotFoundException("ProcessDiagramEntity not found"));
        repository.delete(entity);
    }

    private String qualifiedUpdatedKey(ProcessDiagramEntity existing, String newKey, String tenantId) {
        if (tenantId != null) {
            return tenantContext.qualify(tenantId, newKey);
        }
        String oldActual = actualKey(existing);
        String stored = existing.getProcesskey();
        if (stored != null && oldActual != null && stored.endsWith(oldActual)) {
            return stored.substring(0, stored.length() - oldActual.length()) + newKey;
        }
        return newKey;
    }

    private ProcessDiagramEntity assertAccess(ProcessDiagramEntity entity, String tenantId) {
        if (!tenantContext.belongsToTenant(entity.getProcesskey(), tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return entity;
    }

    private ProcessDiagramEntity copy(ProcessDiagramEntity source) {
        ProcessDiagramEntity target = new ProcessDiagramEntity();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setProcesskey(actualKey(source));
        target.setDescription(source.getDescription());
        target.setCreated(source.getCreated());
        target.setUpdated(source.getUpdated());
        target.setUpdatedBy(source.getUpdatedBy());
        target.setActive(source.getActive());
        target.setType(source.getType());
        target.setVersion(source.getVersion());
        target.setDiagram(source.getDiagram());
        target.setDiagramUsages(source.getDiagramUsages());
        return target;
    }

    private String actualKey(ProcessDiagramEntity entity) {
        if (entity.getDiagram() == null) {
            return tenantContext.unqualify(entity.getProcesskey());
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(entity.getDiagram()));
            String expression = "dmn".equals(entity.getType())
                    ? "string(/*[local-name()='definitions']/@id)"
                    : "string((//*[local-name()='process'])[1]/@id)";
            String key = (String) XPathFactory.newInstance().newXPath()
                    .evaluate(expression, document, XPathConstants.STRING);
            return key == null || key.isBlank() ? tenantContext.unqualify(entity.getProcesskey()) : key;
        } catch (Exception e) {
            return tenantContext.unqualify(entity.getProcesskey());
        }
    }

    private String value(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private <T> List<T> page(List<T> values, int firstResult, int maxResults) {
        int from = Math.min(Math.max(firstResult, 0), values.size());
        int to = (int) Math.min((long) from + Math.max(maxResults, 0), values.size());
        return new ArrayList<>(values.subList(from, to));
    }

    private static class DiagramView implements ProcessDiagramReduce {
        private final ProcessDiagramEntity entity;
        private final String processKey;

        DiagramView(ProcessDiagramEntity entity, String processKey) {
            this.entity = entity;
            this.processKey = processKey;
        }

        public String getId() { return entity.getId(); }
        public String getName() { return entity.getName(); }
        public String getProcesskey() { return processKey; }
        public String getDescription() { return entity.getDescription(); }
        public Timestamp getCreated() { return entity.getCreated(); }
        public Timestamp getUpdated() { return entity.getUpdated(); }
        public Boolean getActive() { return entity.getActive(); }
        public String getType() { return entity.getType(); }
        public Integer getVersion() { return entity.getVersion(); }
    }
}
