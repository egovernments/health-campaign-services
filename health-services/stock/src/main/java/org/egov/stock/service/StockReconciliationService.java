package org.egov.stock.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.stock.web.models.StockReconciliation;
import org.egov.stock.web.models.StockReconciliationBulkRequest;
import org.egov.stock.web.models.StockReconciliationRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class StockReconciliationService {

    public StockReconciliation create(StockReconciliationRequest request) {
        return request.getStockReconciliation();
    }

    public List<StockReconciliation> create(StockReconciliationBulkRequest request, boolean isBulk) {
        return request.getStockReconciliation();
    }

    public StockReconciliation update(StockReconciliationRequest request) {
        return request.getStockReconciliation();
    }

    public List<StockReconciliation> update(StockReconciliationBulkRequest request, boolean isBulk) {
        return request.getStockReconciliation();
    }

    public StockReconciliation delete(StockReconciliationRequest request) {
        return request.getStockReconciliation();
    }

    public List<StockReconciliation> delete(StockReconciliationBulkRequest request, boolean isBulk) {
        return request.getStockReconciliation();
    }

}
