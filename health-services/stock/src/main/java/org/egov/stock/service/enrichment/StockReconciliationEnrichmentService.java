package org.egov.stock.service.enrichment;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.service.IdGenService;
import org.egov.stock.config.StockReconciliationConfiguration;
import org.egov.stock.web.models.StockReconciliation;
import org.egov.stock.web.models.StockReconciliationBulkRequest;
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
public class StockReconciliationEnrichmentService {

    private final IdGenService idGenService;

    private final StockReconciliationConfiguration configuration;

    public StockReconciliationEnrichmentService(IdGenService idGenService, StockReconciliationConfiguration configuration) {
        this.idGenService = idGenService;
        this.configuration = configuration;
    }

    public void create(List<StockReconciliation> entities, StockReconciliationBulkRequest request) throws Exception {
        List<String> idList = idGenService.getIdList(request.getRequestInfo(),
                getTenantId(entities),
                configuration.getStockReconciliationIdFormat(), "", entities.size());

        enrichForCreate(entities, idList, request.getRequestInfo());
    }

    public void update(List<StockReconciliation> entities, StockReconciliationBulkRequest request) {
        Map<String, StockReconciliation> stockMap = getIdToObjMap(entities);
        enrichForUpdate(stockMap, entities, request);
    }

    public void delete(List<StockReconciliation> entities, StockReconciliationBulkRequest request) {
        enrichForDelete(entities, request.getRequestInfo(), true);
    }
}
