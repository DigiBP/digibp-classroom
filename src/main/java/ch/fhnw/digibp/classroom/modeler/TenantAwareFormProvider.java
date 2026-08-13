package ch.fhnw.digibp.classroom.modeler;

import jakarta.persistence.EntityNotFoundException;
import org.cibseven.modeler.model.FormEntity;
import org.cibseven.modeler.provider.FormProvider;
import org.cibseven.modeler.repository.FormRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Primary
@Component
@ConditionalOnProperty(prefix = "cibseven.webclient.modeler", name = "enabled", havingValue = "true")
public class TenantAwareFormProvider extends FormProvider {

    private final FormRepository repository;
    private final ModelerTenantContext tenantContext;
    private final ModelerDeploymentSynchronizer synchronizer;

    public TenantAwareFormProvider(FormRepository repository,
                                   ModelerTenantContext tenantContext,
                                   ModelerDeploymentSynchronizer synchronizer) {
        this.repository = repository;
        this.tenantContext = tenantContext;
        this.synchronizer = synchronizer;
    }

    @Override
    public List<FormEntity> getForms(String keyword, int firstResult, int maxResults) {
        String tenantId = tenantContext.currentTenantId();
        synchronizer.synchronize(tenantId);
        String normalizedKeyword = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        List<FormEntity> result = repository.findAll(Sort.by("updated").descending()).stream()
                .filter(entity -> tenantContext.belongsToTenant(entity.getFormId(), tenantId))
                .map(this::copy)
                .filter(entity -> normalizedKeyword.isEmpty()
                        || value(entity.getFormId()).contains(normalizedKeyword)
                        || value(entity.getDescription()).contains(normalizedKeyword))
                .toList();
        return page(result, firstResult, maxResults);
    }

    @Override
    public List<FormEntity> getForms(int firstResult, int maxResults) {
        return getForms(null, firstResult, maxResults);
    }

    @Override
    public Optional<FormEntity> findById(String id) {
        String tenantId = tenantContext.currentTenantId();
        return repository.findById(id).map(entity -> copy(assertAccess(entity, tenantId)));
    }

    @Override
    public FormEntity createForm(FormEntity entity) {
        String tenantId = tenantContext.currentTenantId();
        entity.setFormId(tenantContext.qualify(tenantId, entity.getFormId()));
        entity.setCreated(Timestamp.valueOf(LocalDateTime.now()));
        entity.setUpdated(Timestamp.valueOf(LocalDateTime.now()));
        return copy(repository.save(entity));
    }

    @Override
    public FormEntity updateForm(FormEntity entity) {
        String tenantId = tenantContext.currentTenantId();
        FormEntity existing = repository.findById(entity.getId())
                .map(item -> assertAccess(item, tenantId))
                .orElseThrow(() -> new EntityNotFoundException("FormEntity not found"));
        existing.setFormSchema(entity.getFormSchema());
        existing.setDescription(entity.getDescription());
        existing.setActive(entity.getActive());
        existing.setUpdated(Timestamp.valueOf(LocalDateTime.now()));
        existing.setUpdatedBy(entity.getUpdatedBy());
        return copy(repository.save(existing));
    }

    @Transactional
    @Override
    public void delete(String id) {
        String tenantId = tenantContext.currentTenantId();
        FormEntity entity = repository.findById(id)
                .map(item -> assertAccess(item, tenantId))
                .orElseThrow(() -> new EntityNotFoundException("FormEntity not found"));
        repository.delete(entity);
    }

    private FormEntity assertAccess(FormEntity entity, String tenantId) {
        if (!tenantContext.belongsToTenant(entity.getFormId(), tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return entity;
    }

    private FormEntity copy(FormEntity source) {
        FormEntity target = new FormEntity();
        target.setId(source.getId());
        target.setDescription(source.getDescription());
        target.setCreated(source.getCreated());
        target.setUpdated(source.getUpdated());
        target.setUpdatedBy(source.getUpdatedBy());
        target.setActive(source.getActive());
        target.setFormSchema(source.getFormSchema());
        target.setFormId(tenantContext.unqualify(source.getFormId()));
        target.setVersion(source.getVersion());
        return target;
    }

    private String value(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private <T> List<T> page(List<T> values, int firstResult, int maxResults) {
        int from = Math.min(Math.max(firstResult, 0), values.size());
        int to = (int) Math.min((long) from + Math.max(maxResults, 0), values.size());
        return new ArrayList<>(values.subList(from, to));
    }
}
