package digit.service.enrichment;

import digit.config.Configuration;
import digit.models.coremodels.AuditDetails;
import digit.web.models.Assumption;
import digit.web.models.File;
import digit.web.models.Operation;
import digit.web.models.PlanConfiguration;
import digit.web.models.PlanConfigurationRequest;
import digit.web.models.ResourceMapping;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Service;

import static digit.config.ServiceConstants.USERINFO_MISSING_CODE;
import static digit.config.ServiceConstants.USERINFO_MISSING_MESSAGE;


@Service
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

        enrichAuditDetails(request.getPlanConfiguration(), request.getRequestInfo().getUserInfo().getUuid(), Boolean.TRUE);
    }

    public PlanConfiguration enrichPlanConfiguration(PlanConfiguration planConfiguration) {
        log.info("enriching plan config with generated IDs");

        // Set ID for PlanConfiguration
        planConfiguration.setId(UUID.randomUUID());

        // Set IDs for Assumptions
        List<Assumption> assumptions = planConfiguration.getAssumptions();
        for (Assumption assumption : assumptions) {
            assumption.setId(UUID.randomUUID());
        }

        // Set IDs for Operations
        List<Operation> operations = planConfiguration.getOperations();
        for (Operation operation : operations) {
            operation.setId(UUID.randomUUID());
        }

        return planConfiguration;
    }

    public void enrichAuditDetails(PlanConfiguration planConfiguration, String by, Boolean isCreate) {
        Long time = System.currentTimeMillis();
        for (Operation operation : planConfiguration.getOperations()) {
            operation.setAuditDetails(getAuditDetails(by, operation.getAuditDetails(), isCreate));
        }

        for (Assumption assumption : planConfiguration.getAssumptions()) {
            assumption.setAuditDetails(getAuditDetails(by, assumption.getAuditDetails(), isCreate));
        }

        for (File file : planConfiguration.getFiles()) {
            file.setAuditDetails(getAuditDetails(by, file.getAuditDetails(), isCreate));
        }

        for (ResourceMapping resourceMapping : planConfiguration.getResourceMapping()) {
            resourceMapping.setAuditDetails(getAuditDetails(by, resourceMapping.getAuditDetails(), isCreate));
        }
    }

    public AuditDetails getAuditDetails(String by, AuditDetails auditDetails, Boolean isCreate) {
        Long time = System.currentTimeMillis();
        if (isCreate)
            return AuditDetails.builder().createdBy(by).lastModifiedBy(by).createdTime(time).lastModifiedTime(time).build();
        else
            return AuditDetails.builder().createdBy(auditDetails.getCreatedBy()).lastModifiedBy(by)
                    .createdTime(auditDetails.getCreatedTime()).lastModifiedTime(time).build();
    }
}
