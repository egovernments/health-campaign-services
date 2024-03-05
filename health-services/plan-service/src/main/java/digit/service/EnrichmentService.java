package digit.service;

import digit.config.Configuration;
import digit.models.coremodels.AuditDetails;
import digit.web.models.Assumption;
import digit.web.models.Operation;
import digit.web.models.PlanConfiguration;
import digit.web.models.PlanConfigurationRequest;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class EnrichmentService {
    private Configuration config;

    public EnrichmentService(Configuration config) {
        this.config = config;
    }

    public void enrichCreate(PlanConfigurationRequest request)  {
        enrichPlanConfiguration(request.getPlanConfiguration());

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

    public AuditDetails getAuditDetails(String by, AuditDetails auditDetails, Boolean isCreate) {
        Long time = System.currentTimeMillis();
        if (isCreate)
            return AuditDetails.builder().createdBy(by).lastModifiedBy(by).createdTime(time).lastModifiedTime(time).build();
        else
            return AuditDetails.builder().createdBy(auditDetails.getCreatedBy()).lastModifiedBy(by)
                    .createdTime(auditDetails.getCreatedTime()).lastModifiedTime(time).build();
    }
}
