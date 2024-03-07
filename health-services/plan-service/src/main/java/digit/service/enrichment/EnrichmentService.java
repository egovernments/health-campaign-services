package digit.service.enrichment;

import digit.config.Configuration;
import digit.web.models.Assumption;
import digit.web.models.File;
import digit.web.models.Operation;
import digit.web.models.PlanConfiguration;
import digit.web.models.PlanConfigurationRequest;
import digit.web.models.ResourceMapping;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.utils.AuditDetailsEnrichmentUtil;
import org.egov.common.utils.UUIDEnrichmentUtil;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

import static digit.config.ServiceConstants.USERINFO_MISSING_CODE;
import static digit.config.ServiceConstants.USERINFO_MISSING_MESSAGE;


@Component
@Slf4j
public class EnrichmentService {
    private Configuration config;

    public EnrichmentService(Configuration config) {
        this.config = config;
    }

    public void enrichCreate(PlanConfigurationRequest request)  {
        enrichPlanConfiguration(request.getPlanConfiguration());
        if(request.getRequestInfo().getUserInfo() == null)
            throw new CustomException(USERINFO_MISSING_CODE, USERINFO_MISSING_MESSAGE);

        enrichAuditDetails(request, Boolean.TRUE);
    }

    public void enrichPlanConfiguration(PlanConfiguration planConfiguration) {
        log.info("enriching plan config with generated IDs");

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

    public void enrichAuditDetails(PlanConfigurationRequest request, Boolean isCreate) {
        // Enrich audit details for plan configurationqq
        PlanConfiguration planConfiguration = request.getPlanConfiguration();
        planConfiguration.setAuditDetails(AuditDetailsEnrichmentUtil
                .prepareAuditDetails(planConfiguration.getAuditDetails(), request.getRequestInfo(), isCreate));
    }

}
