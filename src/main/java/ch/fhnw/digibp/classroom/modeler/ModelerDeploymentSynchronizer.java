package ch.fhnw.digibp.classroom.modeler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cibseven.bpm.engine.RepositoryService;
import org.cibseven.bpm.engine.repository.DecisionDefinition;
import org.cibseven.bpm.engine.repository.DecisionRequirementsDefinition;
import org.cibseven.bpm.engine.repository.Deployment;
import org.cibseven.bpm.engine.repository.ProcessDefinition;
import org.cibseven.bpm.engine.repository.Resource;
import org.cibseven.modeler.model.FormEntity;
import org.cibseven.modeler.model.ProcessDiagramEntity;
import org.cibseven.modeler.repository.FormRepository;
import org.cibseven.modeler.repository.ProcessDiagramRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

@Component
@ConditionalOnProperty(prefix = "cibseven.webclient.modeler", name = "enabled", havingValue = "true")
public class ModelerDeploymentSynchronizer {

    private static final String SYNCHRONIZED_BY = "deployment-sync:";
    private static final Logger LOGGER = Logger.getLogger(ModelerDeploymentSynchronizer.class.getName());

    private final RepositoryService repositoryService;
    private final ProcessDiagramRepository diagramRepository;
    private final FormRepository formRepository;
    private final ModelerTenantContext tenantContext;
    private final ObjectMapper objectMapper;

    public ModelerDeploymentSynchronizer(RepositoryService repositoryService,
                                         ProcessDiagramRepository diagramRepository,
                                         FormRepository formRepository,
                                         ModelerTenantContext tenantContext,
                                         ObjectMapper objectMapper) {
        this.repositoryService = repositoryService;
        this.diagramRepository = diagramRepository;
        this.formRepository = formRepository;
        this.tenantContext = tenantContext;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void synchronize(String tenantId) {
        if (tenantId == null) {
            synchronizeDeployments(null, repositoryService.createDeploymentQuery().withoutTenantId().list());
            identityTenantIds().forEach(this::synchronize);
            return;
        }

        synchronizeDeployments(tenantId,
                repositoryService.createDeploymentQuery().tenantIdIn(tenantId).list());
    }

    private void synchronizeDeployments(String tenantId, List<Deployment> deployments) {
        deployments = deployments.stream()
                .sorted(Comparator.comparing(Deployment::getDeploymentTime))
                .toList();

        for (Deployment deployment : deployments) {
            for (Resource resource : repositoryService.getDeploymentResources(deployment.getId())) {
                synchronizeResource(tenantId, deployment, resource);
            }
        }
    }

    private List<String> identityTenantIds() {
        return repositoryService.createDeploymentQuery().list().stream()
                .map(Deployment::getTenantId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
    }

    private void synchronizeResource(String tenantId, Deployment deployment, Resource resource) {
        String name = resource.getName();
        String lowerName = name.toLowerCase(Locale.ROOT);
        boolean bpmn = lowerName.endsWith(".bpmn") || lowerName.endsWith(".bpmn20.xml");
        boolean dmn = lowerName.endsWith(".dmn") || lowerName.endsWith(".dmn11.xml");
        if (!bpmn && !dmn && !lowerName.endsWith(".form")) {
            return;
        }

        try (InputStream input = repositoryService.getResourceAsStream(deployment.getId(), name)) {
            byte[] data = input.readAllBytes();
            Timestamp deployedAt = new Timestamp(deployment.getDeploymentTime().getTime());
            if (lowerName.endsWith(".form")) {
                synchronizeForm(tenantId, name, data, deployedAt);
            } else {
                synchronizeDiagram(tenantId, deployment.getId(), name, data, deployedAt,
                        dmn ? "dmn" : "bpmn-c7");
            }
        } catch (IOException e) {
            LOGGER.warning("Skipping deployed artifact that the Modeler cannot read: " + name);
        }
    }

    private void synchronizeDiagram(String tenantId, String deploymentId, String resourceName,
                                    byte[] data, Timestamp deployedAt, String type) {
        String artifactId;
        String artifactName;
        if ("dmn".equals(type)) {
            DecisionRequirementsDefinition requirementsDefinition = repositoryService
                    .createDecisionRequirementsDefinitionQuery()
                    .deploymentId(deploymentId).list().stream()
                    .filter(item -> resourceName.equals(item.getResourceName()))
                    .findFirst().orElse(null);
            if (requirementsDefinition != null) {
                artifactId = requirementsDefinition.getKey();
                artifactName = requirementsDefinition.getName();
            } else {
                DecisionDefinition definition = repositoryService.createDecisionDefinitionQuery()
                        .deploymentId(deploymentId).list().stream()
                        .filter(item -> resourceName.equals(item.getResourceName()))
                        .findFirst().orElse(null);
                if (definition == null) {
                    return;
                }
                artifactId = definition.getKey();
                artifactName = definition.getName();
            }
        } else {
            ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                    .deploymentId(deploymentId).list().stream()
                    .filter(item -> resourceName.equals(item.getResourceName()))
                    .findFirst().orElse(null);
            if (definition == null) {
                return;
            }
            artifactId = definition.getKey();
            artifactName = definition.getName();
        }

        String storedKey = tenantContext.qualify(tenantId, artifactId);
        ProcessDiagramEntity entity = diagramRepository.findByProcesskey(storedKey);
        if (entity == null) {
            entity = new ProcessDiagramEntity();
            entity.setCreated(deployedAt);
            entity.setProcesskey(storedKey);
            entity.setActive(true);
            entity.setVersion(1);
        } else if (entity.getUpdated() != null && entity.getUpdated().after(deployedAt)) {
            return;
        } else if (Arrays.equals(entity.getDiagram(), data)) {
            return;
        }
        entity.setName(artifactName == null || artifactName.isBlank() ? artifactId : artifactName);
        entity.setType(type);
        entity.setDiagram(data);
        entity.setUpdated(deployedAt);
        entity.setUpdatedBy(SYNCHRONIZED_BY + tenantId);
        diagramRepository.save(entity);
    }

    private void synchronizeForm(String tenantId, String resourceName, byte[] data, Timestamp deployedAt)
            throws IOException {
        String fallbackId = resourceName.substring(0, resourceName.length() - ".form".length());
        JsonNode schema = objectMapper.readTree(data);
        String artifactId = schema.path("id").asText(fallbackId);
        if (artifactId.isBlank()) {
            artifactId = fallbackId;
        }

        String storedId = tenantContext.qualify(tenantId, artifactId);
        FormEntity entity = formRepository.findAll().stream()
                .filter(item -> storedId.equals(item.getFormId()))
                .findFirst().orElse(null);
        if (entity == null) {
            entity = new FormEntity();
            entity.setCreated(deployedAt);
            entity.setFormId(storedId);
            entity.setActive(true);
            entity.setVersion(1);
        } else if (entity.getUpdated() != null && entity.getUpdated().after(deployedAt)) {
            return;
        } else if (Arrays.equals(entity.getFormSchema(), data)) {
            return;
        }
        entity.setFormSchema(data);
        entity.setUpdated(deployedAt);
        entity.setUpdatedBy(SYNCHRONIZED_BY + tenantId);
        formRepository.save(entity);
    }
}
