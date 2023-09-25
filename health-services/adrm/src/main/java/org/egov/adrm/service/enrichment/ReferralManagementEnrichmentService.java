package org.egov.adrm.service.enrichment;

import lombok.extern.slf4j.Slf4j;
import org.egov.adrm.config.AdrmConfiguration;
import org.egov.adrm.repository.ReferralManagementRepository;
import org.egov.common.models.adrm.referralmanagement.Referral;
import org.egov.common.models.adrm.referralmanagement.ReferralBulkRequest;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.enrichForDelete;
import static org.egov.common.utils.CommonUtils.enrichForUpdate;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;

@Component
@Slf4j
public class ReferralManagementEnrichmentService {
    private final IdGenService idGenService;

    private final AdrmConfiguration adrmConfiguration;

    private final ReferralManagementRepository referralManagementRepository;

    public ReferralManagementEnrichmentService(IdGenService idGenService, AdrmConfiguration adrmConfiguration, ReferralManagementRepository referralManagementRepository) {
        this.idGenService = idGenService;
        this.adrmConfiguration = adrmConfiguration;
        this.referralManagementRepository = referralManagementRepository;
    }

    public void create(List<Referral> entities, ReferralBulkRequest request) throws Exception {
        log.info("starting the enrichment for create referrals");
        log.info("generating IDs using UUID");
        List<String> idList = CommonUtils.uuidSupplier().apply(entities.size());
        log.info("enriching referrals with generated IDs");
        enrichForCreate(entities, idList, request.getRequestInfo());
        log.info("enrichment done");
    }

    public void update(List<Referral> entities, ReferralBulkRequest request) {
        log.info("starting the enrichment for create referrals");
        Map<String, Referral> referralMap = getIdToObjMap(entities);
        enrichForUpdate(referralMap, entities, request);
        log.info("enrichment done");
    }

    public void delete(List<Referral> entities, ReferralBulkRequest request) {
        log.info("starting the enrichment for delete referrals");
        enrichForDelete(entities, request.getRequestInfo(), true);
        log.info("enrichment done");
    }
}
