package org.egov.stock.service.enrichment;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockBulkRequest;
import org.egov.common.service.IdGenService;
import org.egov.stock.config.StockConfiguration;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.enrichForDelete;
import static org.egov.common.utils.CommonUtils.enrichForUpdate;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getTenantId;


@Service
@Slf4j
public class StockEnrichmentService {

    private final IdGenService idGenService;

    private final StockConfiguration configuration;

    public StockEnrichmentService(IdGenService idGenService, StockConfiguration configuration) {
        this.idGenService = idGenService;
        this.configuration = configuration;
    }

    public void create(List<Stock> entities, StockBulkRequest request) throws Exception {
        log.info("starting create method for stock enrichment");
        log.info("generating IDs for stock enrichment using IdGenService");
        List<String> idList = idGenService.getIdList(request.getRequestInfo(),
                getTenantId(entities),
                configuration.getStockIdFormat(), "", entities.size());

        log.info("enriching stock enrichment with generated IDs");
        enrichForCreate(entities, idList, request.getRequestInfo());

        log.info("completed create method for stock enrichment");
    }

    public void update(List<Stock> entities, StockBulkRequest request) {
        log.info("starting update method for stock enrichment");
        Map<String, Stock> stockMap = getIdToObjMap(entities);

        log.info("enriching stock enrichment with generated IDs");
        enrichForUpdate(stockMap, entities, request);

        log.info("completed update method for stock enrichment");
    }

    public void delete(List<Stock> entities, StockBulkRequest request) {
        log.info("starting delete method for stock enrichment");

        log.info("enriching stock enrichment with generated IDs");
        enrichForDelete(entities, request.getRequestInfo(), true);

        log.info("completed delete method for stock enrichment");
    }
}
