package org.egov.stock.service.enrichment;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.common.models.stock.StockReconciliationBulkRequest;
import org.egov.common.service.IdGenService;
import org.egov.stock.config.StockReconciliationConfiguration;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.*;

@Service
@Slf4j
public class StockReconciliationEnrichmentService {

    private final IdGenService idGenService;

    private final StockReconciliationConfiguration configuration;

    public StockReconciliationEnrichmentService(IdGenService idGenService, StockReconciliationConfiguration configuration) {
        this.idGenService = idGenService;
        this.configuration = configuration;
    }

    public void create(List<StockReconciliation> entities, StockReconciliationBulkRequest request) throws Exception {
        log.info("starting create method for stock reconciliation enrichment");

        log.info("generating IDs for stock reconciliation enrichment using IdGenService");
        List<String> idList = idGenService.getIdList(request.getRequestInfo(),
                getTenantId(entities),
                configuration.getStockReconciliationIdFormat(), "", entities.size());

        log.info("enriching stock reconciliation enrichment with generated IDs");
        enrichForCreate(entities, idList, request.getRequestInfo());

        log.info("completed create method for stock reconciliation enrichment");
    }

    public void update(List<StockReconciliation> entities, StockReconciliationBulkRequest request) {
        log.info("starting update method for stock reconciliation enrichment");

        Map<String, StockReconciliation> stockMap = getIdToObjMap(entities);

        log.info("enriching stock reconciliation enrichment with generated IDs");
        enrichForUpdate(stockMap, entities, request);

        log.info("completed update method for stock reconciliation enrichment");
    }

    public void delete(List<StockReconciliation> entities, StockReconciliationBulkRequest request) {
        log.info("starting delete method for stock reconciliation enrichment");

        log.info("enriching stock reconciliation enrichment with generated IDs");
        enrichForDelete(entities, request.getRequestInfo(), true);

        log.info("completed delete method for stock reconciliation enrichment");
    }
}
