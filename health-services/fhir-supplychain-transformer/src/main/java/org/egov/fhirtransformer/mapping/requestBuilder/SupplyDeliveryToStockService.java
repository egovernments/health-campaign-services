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
 * Service responsible for transforming FHIR SupplyDelivery–derived
 * {@link Stock} data into DIGIT Stock service requests.
 */
@Service
public class SupplyDeliveryToStockService {

    @Autowired
    private ApiIntegrationService apiIntegrationService;

    @Autowired
    private GenericCreateOrUpdateService genericCreateOrUpdateService;

    @Value("${stock.create.url}")
    private String stockCreateUrl;

    @Value("${stock.update.url}")
    private String stockUpdateUrl;

    /**
     * Transforms and persists Stock records derived from SupplyDelivery resources.
     * @param supplyDeliveryMap map of Stock ID to Stock data;
     *                          may be empty but not {@code null}
     * @return map containing processing metrics
     * @throws Exception if transformation or API invocation fails
     */
    public HashMap<String, Integer> transformSupplyDeliveryToStock(HashMap<String, Stock> supplyDeliveryMap, RequestInfo requestInfo) throws Exception {
        return genericCreateOrUpdateService.process(supplyDeliveryMap,
                (stockIds) -> fetchExistingStockIds(stockIds, requestInfo),
                (toCreate, createUrl) -> createStocks(toCreate, createUrl, requestInfo),
                (toUpdate, updateUrl) -> updateStocks(toUpdate, updateUrl, requestInfo),
                stockCreateUrl,
                stockUpdateUrl,
                requestInfo,
                "Error in transformSupplyDeliveryToStock");
    }

    // Adapter: fetch existing stock ids
    private List<String> fetchExistingStockIds(List<String> stockIds, RequestInfo requestInfo) throws Exception {
        try{
            URLParams urlParams = apiIntegrationService.formURLParams(stockIds);

            StockSearch stockSearch = new StockSearch();
            stockSearch.setId(stockIds);

            StockSearchRequest stockSearchRequest = new StockSearchRequest();
            stockSearchRequest.setRequestInfo(requestInfo);
            stockSearchRequest.setStock(stockSearch);

            StockBulkResponse stockBulkResponse = apiIntegrationService.fetchAllStocks(urlParams, stockSearchRequest);

            if (stockBulkResponse.getStock() == null){
                return new ArrayList<>();
            }
            List<String> existingIds = new ArrayList<>();
            for (Stock stock : stockBulkResponse.getStock()) {
                existingIds.add(stock.getId());
            }
            return existingIds;
        } catch (Exception e){
            throw new Exception("Error in fetchExistingStocks: " + e.getMessage());
        }
    }

    // Adapter: create stocks
    private void createStocks(List<Stock> toCreate, String createUrl, RequestInfo requestInfo) throws Exception {
        try{
            if (toCreate == null || toCreate.isEmpty()) return;
            StockBulkRequest stockBulkRequest = new StockBulkRequest();
            stockBulkRequest.setRequestInfo(requestInfo);
            stockBulkRequest.setStock(toCreate);
            apiIntegrationService.sendRequestToAPI(stockBulkRequest, createUrl);
        } catch (Exception e) {
            throw new Exception("Error in createStocks: " + e.getMessage());
        }
    }

    // Adapter: update stocks
    private void updateStocks(List<Stock> toUpdate, String updateUrl, RequestInfo requestInfo) throws Exception {
        try{
            if (toUpdate == null || toUpdate.isEmpty()) return;
            StockBulkRequest stockBulkRequest = new StockBulkRequest();
            stockBulkRequest.setRequestInfo(requestInfo);
            stockBulkRequest.setStock(toUpdate);
            apiIntegrationService.sendRequestToAPI(stockBulkRequest, updateUrl);
        } catch (Exception e) {
            throw new Exception("Error in updateStocks: " + e.getMessage());
        }
    }
}
