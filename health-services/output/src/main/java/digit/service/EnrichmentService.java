package digit.service;

import digit.config.Configuration;
import digit.config.ServiceConstants;
import digit.util.IdgenUtil;
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

    private final IdgenUtil idgenUtil;

    private final Configuration config;

    public EnrichmentService(IdgenUtil idgenUtil, Configuration config) {
        this.idgenUtil = idgenUtil;
        this.config = config;
    }

    public void create(PlanConfigurationRequest request) throws Exception {
        enrichPlanConfiguration(request.getPlanConfiguration());

        log.info("enriching facility enrichment with generated IDs");
    }

    public static PlanConfiguration enrichPlanConfiguration(PlanConfiguration planConfiguration) {
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
}
