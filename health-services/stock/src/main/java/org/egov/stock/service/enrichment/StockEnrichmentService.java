package org.egov.stock.service.enrichment;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.service.IdGenService;
import org.egov.stock.config.StockConfiguration;
import org.egov.stock.web.models.Stock;
import org.egov.stock.web.models.StockBulkRequest;
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
        List<String> idList = idGenService.getIdList(request.getRequestInfo(),
                getTenantId(entities),
                configuration.getStockIdFormat(), "", entities.size());

        enrichForCreate(entities, idList, request.getRequestInfo());
    }

    public void update(List<Stock> entities, StockBulkRequest request) {
        Map<String, Stock> stockMap = getIdToObjMap(entities);
        enrichForUpdate(stockMap, entities, request);
    }

    public void delete(List<Stock> entities, StockBulkRequest request) {
        enrichForDelete(entities, request.getRequestInfo(), true);
    }
}
