package org.egov.referralmanagement.service.enrichment;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralBulkRequest;
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

    /**
     *
     * @param entities
     * @param request
     */
    public void create(List<Referral> entities, ReferralBulkRequest request) {
        log.info("starting the enrichment for create referrals");
        log.info("generating IDs using UUID");
        List<String> idList = CommonUtils.uuidSupplier().apply(entities.size());
        log.info("enriching referrals with generated IDs");
        enrichForCreate(entities, idList, request.getRequestInfo());
        log.info("enrichment done");
    }

    /**
     *
     * @param entities
     * @param request
     */
    public void update(List<Referral> entities, ReferralBulkRequest request) {
        log.info("starting the enrichment for create referrals");
        Map<String, Referral> referralMap = getIdToObjMap(entities);
        enrichForUpdate(referralMap, entities, request);
        log.info("enrichment done");
    }

    /**
     *
     * @param entities
     * @param request
     */
    public void delete(List<Referral> entities, ReferralBulkRequest request) {
        log.info("starting the enrichment for delete referrals");
        enrichForDelete(entities, request.getRequestInfo(), true);
        log.info("enrichment done");
    }
}
