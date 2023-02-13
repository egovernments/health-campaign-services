package org.egov.stock.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.stock.web.models.StockReconciliation;
import org.egov.stock.web.models.StockReconciliationRequest;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class StockReconciliationService {

    public StockReconciliation create(StockReconciliationRequest request) {
        return request.getStockReconciliation();
    }

    public StockReconciliation update(StockReconciliationRequest request) {
        return request.getStockReconciliation();
    }

    public StockReconciliation delete(StockReconciliationRequest request) {
        return request.getStockReconciliation();
    }

}
