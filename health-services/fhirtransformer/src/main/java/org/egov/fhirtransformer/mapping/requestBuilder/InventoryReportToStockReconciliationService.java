package org.egov.fhirtransformer.mapping.requestBuilder;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.stock.*;
import org.egov.fhirtransformer.service.ApiIntegrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Service responsible for transforming FHIR InventoryReport–derived
 * {@link StockReconciliation} data into DIGIT Stock Reconciliation requests.
 */
@Service
public class InventoryReportToStockReconciliationService {

    @Autowired
    private ApiIntegrationService apiIntegrationService;

    @Autowired
    private GenericCreateOrUpdateService genericCreateOrUpdateService;

    @Value("${stock.recon.create.url}")
    private String stockReconCreateUrl;

    @Value("${stock.recon.update.url}")
    private String stockReconUpdateUrl;

    private RequestInfo requestInfo;

    /**
     * Transforms and persists StockReconciliation records derived from InventoryReports.
     * @param stockReconciliationMap map of StockReconciliation ID to data;
     *                               may be empty but not {@code null}
     * @return map containing processing metrics
     * @throws Exception if transformation or API invocation fails
     */
    public HashMap<String, Integer> transformInventoryReportToStockReconciliation(HashMap<String, StockReconciliation> stockReconciliationMap, RequestInfo requestInfo) throws Exception {

        this.requestInfo = requestInfo;

        return genericCreateOrUpdateService.process(stockReconciliationMap,
                this::fetchExistingStockReconIds,
                this::createStockRecon,
                this::updateStockRecon,
                stockReconCreateUrl,
                stockReconUpdateUrl,
                requestInfo,
                "Error in transformInventoryReportToStockReconciliation");
    }

    // Adapter: fetch existing stock reconciliation ids
    public List<String> fetchExistingStockReconIds(List<String> stockReconIds) throws Exception {
        HashMap<String,List<String>> newandexistingids = new HashMap<>();
        try{
            URLParams urlParams = apiIntegrationService.formURLParams(stockReconIds);
            StockReconciliationSearch stockReconciliationSearch = new StockReconciliationSearch();
            stockReconciliationSearch.setId(stockReconIds);

            StockReconciliationSearchRequest stockReconciliationSearchRequest = new StockReconciliationSearchRequest();
            stockReconciliationSearchRequest.setRequestInfo(this.requestInfo);
            stockReconciliationSearchRequest.setStockReconciliation(stockReconciliationSearch);

            StockReconciliationBulkResponse stockBulkReconResponse = apiIntegrationService.fetchAllStockReconciliation(urlParams, stockReconciliationSearchRequest);
            if (stockBulkReconResponse.getStockReconciliation() == null || stockBulkReconResponse.getStockReconciliation().isEmpty()){
                return new ArrayList<>();
            }
            List<String> existingIds = new ArrayList<>();
            for (StockReconciliation stockRecon : stockBulkReconResponse.getStockReconciliation()) {
                existingIds.add(stockRecon.getId());
            }
            return existingIds;
        } catch (Exception e){
            throw new Exception("Error in fetchExisting StockRecon: " + e.getMessage());
        }
    }

    // Adapter: create stock reconciliations
    public void createStockRecon(List<StockReconciliation> toCreate, String createUrl) throws Exception {
        try{
            if (toCreate == null || toCreate.isEmpty()) return;
            StockReconciliationBulkRequest stockReconciliationBulkRequest = new StockReconciliationBulkRequest();
            stockReconciliationBulkRequest.setRequestInfo(this.requestInfo);
            stockReconciliationBulkRequest.setStockReconciliation(toCreate);
            apiIntegrationService.sendRequestToAPI(stockReconciliationBulkRequest, createUrl);
        } catch (Exception e) {
            throw new Exception("Error in createStockRecon: " + e.getMessage());
        }
    }

    // Adapter: update stock reconciliations
    public void updateStockRecon(List<StockReconciliation> toUpdate, String updateUrl) throws Exception {
        try{
            if (toUpdate == null || toUpdate.isEmpty()) return;
            StockReconciliationBulkRequest stockReconciliationBulkRequest = new StockReconciliationBulkRequest();
            stockReconciliationBulkRequest.setRequestInfo(this.requestInfo);
            stockReconciliationBulkRequest.setStockReconciliation(toUpdate);
            apiIntegrationService.sendRequestToAPI(stockReconciliationBulkRequest, updateUrl);
        } catch (Exception e) {
            throw new Exception("Error in updateStockRecon: " + e.getMessage());
        }
    }
}
