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

        enrichAuditDetails(request.getPlanConfiguration(), request.getRequestInfo().getUserInfo().getUuid(), Boolean.TRUE);
    }

    public void enrichPlanConfiguration(PlanConfiguration planConfiguration) {
        log.info("enriching plan config with generated IDs");
        UUIDEnrichmentUtil.enrichRandomUuid(planConfiguration, "id");
        UUIDEnrichmentUtil.enrichRandomUuid(planConfiguration.getFiles(), "id");
        UUIDEnrichmentUtil.enrichRandomUuid(planConfiguration.getAssumptions(), "id");
        UUIDEnrichmentUtil.enrichRandomUuid(planConfiguration.getOperations(), "id");
        UUIDEnrichmentUtil.enrichRandomUuid(planConfiguration.getResourceMapping(), "id");

    }

    public void enrichAuditDetails(PlanConfiguration planConfiguration, String by, Boolean isCreate) {
        Long time = System.currentTimeMillis();
        planConfiguration.setAuditDetails(getAuditDetails(by,planConfiguration.getAuditDetails(), isCreate));
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
