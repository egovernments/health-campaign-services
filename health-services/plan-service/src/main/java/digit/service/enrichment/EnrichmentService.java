package digit.service.enrichment;

import digit.config.Configuration;
import digit.web.models.PlanConfiguration;
import digit.web.models.PlanConfigurationRequest;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.utils.AuditDetailsEnrichmentUtil;
import org.egov.common.utils.UUIDEnrichmentUtil;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

import static digit.config.ServiceConstants.USERINFO_MISSING_CODE;
import static digit.config.ServiceConstants.USERINFO_MISSING_MESSAGE;
import org.springframework.util.ObjectUtils;

@Component
@Slf4j
public class EnrichmentService {
    private Configuration config;

    public EnrichmentService(Configuration config) {
        this.config = config;
    }

    /**
     * Enriches the PlanConfigurationRequest for creating a new plan configuration.
     * This method enriches the plan configuration with generated IDs, validates user information, and enriches audit details for create operation.
     * @param request The PlanConfigurationRequest to be enriched.
     * @throws CustomException if user information is missing in the request.
     */
    public void enrichCreate(PlanConfigurationRequest request) {
        enrichPlanConfiguration(request.getPlanConfiguration());
        if (request.getRequestInfo().getUserInfo() == null)
            throw new CustomException(USERINFO_MISSING_CODE, USERINFO_MISSING_MESSAGE);

        enrichAuditDetails(request, Boolean.TRUE);
    }

    /**
     * Enriches the given plan configuration with generated IDs for plan, files, assumptions, operations, and resource mappings.
     * @param planConfiguration The PlanConfiguration to be enriched.
     */
    public void enrichPlanConfiguration(PlanConfiguration planConfiguration) {
        log.info("Enriching plan config with generated IDs");

        // Generate id for plan configuration
        UUIDEnrichmentUtil.enrichRandomUuid(planConfiguration, "id");

        // Generate id for files
        planConfiguration.getFiles().forEach(file -> UUIDEnrichmentUtil.enrichRandomUuid(file, "id"));

        // Generate id for assumptions
        planConfiguration.getAssumptions().forEach(assumption -> UUIDEnrichmentUtil.enrichRandomUuid(assumption, "id"));

        // Generate id for operations
        planConfiguration.getOperations().forEach(operation -> UUIDEnrichmentUtil.enrichRandomUuid(operation, "id"));

        // Generate id for resource mappings
        planConfiguration.getResourceMapping().forEach(resourceMapping -> UUIDEnrichmentUtil.enrichRandomUuid(resourceMapping, "id"));
    }

    /**
     * Enriches the audit details for the PlanConfigurationRequest based on the operation type (create or update).
     * @param request The PlanConfigurationRequest for which audit details are to be enriched.
     * @param isCreate A boolean indicating whether the operation is a create or update operation.
     */
    public void enrichAuditDetails(PlanConfigurationRequest request, Boolean isCreate) {
        PlanConfiguration planConfiguration = request.getPlanConfiguration();
        planConfiguration.setAuditDetails(AuditDetailsEnrichmentUtil
                .prepareAuditDetails(planConfiguration.getAuditDetails(), request.getRequestInfo(), isCreate));
    }

    /**
     * Enriches the PlanConfigurationRequest for updating an existing plan configuration.
     * This method enriches the plan configuration for update, validates user information, and enriches audit details for update operation.
     * @param request The PlanConfigurationRequest to be enriched.
     * @throws CustomException if user information is missing in the request.
     */
    public void enrichUpdate(PlanConfigurationRequest request) {
        enrichPlanConfigurationForUpdate(request);
        if (request.getRequestInfo().getUserInfo() == null)
            throw new CustomException(USERINFO_MISSING_CODE, USERINFO_MISSING_MESSAGE);

        enrichAuditDetails(request, Boolean.FALSE);
    }

    /**
     * Enriches the plan configuration for update by generating IDs for files, assumptions, operations, and resource mappings if they are empty.
     * @param request The PlanConfigurationRequest to be enriched for update operation.
     */
    public void enrichPlanConfigurationForUpdate(PlanConfigurationRequest request) {
        PlanConfiguration planConfiguration = request.getPlanConfiguration();

        // For Files
        planConfiguration.getFiles().forEach(file -> {
            if (ObjectUtils.isEmpty(file.getId())) {
                UUIDEnrichmentUtil.enrichRandomUuid(file, "id");
            }
        });

        // For Assumptions
        planConfiguration.getAssumptions().forEach(assumption -> {
            if (ObjectUtils.isEmpty(assumption.getId())) {
                UUIDEnrichmentUtil.enrichRandomUuid(assumption, "id");
            }
        });

        // For Operations
        planConfiguration.getOperations().forEach(operation -> {
            if (ObjectUtils.isEmpty(operation.getId())) {
                UUIDEnrichmentUtil.enrichRandomUuid(operation, "id");
            }
        });

        // For ResourceMappings
        planConfiguration.getResourceMapping().forEach(resourceMapping -> {
            if (ObjectUtils.isEmpty(resourceMapping.getId())) {
                UUIDEnrichmentUtil.enrichRandomUuid(resourceMapping, "id");
            }
        });
    }

}
