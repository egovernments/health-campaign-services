package org.egov.referralmanagement.service.enrichment;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectBulkRequest;
import org.egov.common.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.enrichForDelete;
import static org.egov.common.utils.CommonUtils.enrichForUpdate;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;

/**
 * Class use to enrich SideEffectBulkRequest object
 */
@Component
@Slf4j
public class SideEffectEnrichmentService {

    /**
     *
     * @param entities
     * @param request
     */
    public void create(List<SideEffect> entities, SideEffectBulkRequest request) {
        log.info("starting the enrichment for create side effect");
        log.info("generating IDs using UUID");
        List<String> idList = CommonUtils.uuidSupplier().apply(entities.size());
        log.info("enriching side effects with generated IDs");
        enrichForCreate(entities, idList, request.getRequestInfo());
        log.info("enrichment done");
    }

    /**
     *
     * @param entities
     * @param request
     */
    public void update(List<SideEffect> entities, SideEffectBulkRequest request) {
        log.info("starting the enrichment for create side effect");
        Map<String, SideEffect> sideEffectMap = getIdToObjMap(entities);
        enrichForUpdate(sideEffectMap, entities, request);
        log.info("enrichment done");
    }

    /**
     *
     * @param entities
     * @param request
     */
    public void delete(List<SideEffect> entities, SideEffectBulkRequest request) {
        log.info("starting the enrichment for delete side effect");
        enrichForDelete(entities, request.getRequestInfo(), true);
        log.info("enrichment done");
    }
}
