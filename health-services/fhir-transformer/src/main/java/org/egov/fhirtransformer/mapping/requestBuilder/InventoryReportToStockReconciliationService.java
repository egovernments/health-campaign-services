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

    /**
     * Transforms and persists StockReconciliation records derived from InventoryReports.
     * @param stockReconciliationMap map of StockReconciliation ID to data;
     *                               may be empty but not {@code null}
     * @return map containing processing metrics
     * @throws Exception if transformation or API invocation fails
     */
    public HashMap<String, Integer> transformInventoryReportToStockReconciliation(HashMap<String, StockReconciliation> stockReconciliationMap, RequestInfo requestInfo) throws Exception {
        return genericCreateOrUpdateService.process(stockReconciliationMap,
                (stockReconIds) -> fetchExistingStockReconIds(stockReconIds, requestInfo),
                (toCreate, createUrl) -> createStockRecon(toCreate, createUrl, requestInfo),
                (toUpdate, updateUrl) -> updateStockRecon(toUpdate, updateUrl, requestInfo),
                stockReconCreateUrl,
                stockReconUpdateUrl,
                requestInfo,
                "Error in transformInventoryReportToStockReconciliation");
    }

    // Adapter: fetch existing stock reconciliation ids
    private List<String> fetchExistingStockReconIds(List<String> stockReconIds, RequestInfo requestInfo) throws Exception {
        HashMap<String,List<String>> newandexistingids = new HashMap<>();
        try{
            URLParams urlParams = apiIntegrationService.formURLParams(stockReconIds);
            StockReconciliationSearch stockReconciliationSearch = new StockReconciliationSearch();
            stockReconciliationSearch.setId(stockReconIds);

            StockReconciliationSearchRequest stockReconciliationSearchRequest = new StockReconciliationSearchRequest();
            stockReconciliationSearchRequest.setRequestInfo(requestInfo);
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
    private void createStockRecon(List<StockReconciliation> toCreate, String createUrl, RequestInfo requestInfo) throws Exception {
        try{
            if (toCreate == null || toCreate.isEmpty()) return;
            StockReconciliationBulkRequest stockReconciliationBulkRequest = new StockReconciliationBulkRequest();
            stockReconciliationBulkRequest.setRequestInfo(requestInfo);
            stockReconciliationBulkRequest.setStockReconciliation(toCreate);
            apiIntegrationService.sendRequestToAPI(stockReconciliationBulkRequest, createUrl);
        } catch (Exception e) {
            throw new Exception("Error in createStockRecon: " + e.getMessage());
        }
    }

    // Adapter: update stock reconciliations
    private void updateStockRecon(List<StockReconciliation> toUpdate, String updateUrl, RequestInfo requestInfo) throws Exception {
        try{
            if (toUpdate == null || toUpdate.isEmpty()) return;
            StockReconciliationBulkRequest stockReconciliationBulkRequest = new StockReconciliationBulkRequest();
            stockReconciliationBulkRequest.setRequestInfo(requestInfo);
            stockReconciliationBulkRequest.setStockReconciliation(toUpdate);
            apiIntegrationService.sendRequestToAPI(stockReconciliationBulkRequest, updateUrl);
        } catch (Exception e) {
            throw new Exception("Error in updateStockRecon: " + e.getMessage());
        }
    }
}
