package org.egov.adrm.service.enrichment;

import lombok.extern.slf4j.Slf4j;
import org.egov.adrm.config.AdrmConfiguration;
import org.egov.common.models.adrm.adverseevent.AdverseEvent;
import org.egov.common.models.adrm.adverseevent.AdverseEventBulkRequest;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.adrm.repository.AdverseEventRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.*;

@Component
@Slf4j
public class AdverseEventEnrichmentService {

    private final IdGenService idGenService;

    private final AdrmConfiguration adrmConfiguration;

    private final AdverseEventRepository adverseEventRepository;

    public AdverseEventEnrichmentService(IdGenService idGenService, AdrmConfiguration adrmConfiguration, AdverseEventRepository adverseEventRepository) {
        this.idGenService = idGenService;
        this.adrmConfiguration = adrmConfiguration;
        this.adverseEventRepository = adverseEventRepository;
    }

    public void create(List<AdverseEvent> entities, AdverseEventBulkRequest request) throws Exception {
        log.info("starting the enrichment for create adverse event");
        log.info("generating IDs using UUID");
        List<String> idList = CommonUtils.uuidSupplier().apply(entities.size());
        log.info("enriching adverse events with generated IDs");
        enrichForCreate(entities, idList, request.getRequestInfo());
        log.info("enrichment done");
    }

    public void update(List<AdverseEvent> entities, AdverseEventBulkRequest request) {
        log.info("starting the enrichment for create adverse event");
        Map<String, AdverseEvent> adverseEventMap = getIdToObjMap(entities);
        enrichForUpdate(adverseEventMap, entities, request);
        log.info("enrichment done");
    }

    public void delete(List<AdverseEvent> entities, AdverseEventBulkRequest request) {
        log.info("starting the enrichment for delete adverse event");
        enrichForDelete(entities, request.getRequestInfo(), true);
        log.info("enrichment done");
    }
}
