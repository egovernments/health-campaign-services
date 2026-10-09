package org.egov.referralmanagement.service.enrichment;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralBulkRequest;
import org.egov.common.utils.CommonUtils;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.enrichForDelete;
import static org.egov.common.utils.CommonUtils.enrichForUpdate;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;

@Component
@Slf4j
public class HFReferralEnrichmentService {

    /**
     *
     * @param entities
     * @param request
     */
    public void create(List<HFReferral> entities, HFReferralBulkRequest request) {
        log.info("starting the enrichment for create hfReferrals");
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
    public void update(List<HFReferral> entities, HFReferralBulkRequest request) {
        log.info("starting the enrichment for create hfReferrals");
        Map<String, HFReferral> referralMap = getIdToObjMap(entities);
        enrichForUpdate(referralMap, entities, request);
        log.info("enrichment done");
    }

    /**
     *
     * @param entities
     * @param request
     */
    public void delete(List<HFReferral> entities, HFReferralBulkRequest request) {
        log.info("starting the enrichment for delete hfReferrals");
        enrichForDelete(entities, request.getRequestInfo(), true);
        log.info("enrichment done");
    }
}
