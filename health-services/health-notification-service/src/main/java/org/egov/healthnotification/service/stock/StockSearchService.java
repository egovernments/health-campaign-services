package org.egov.healthnotification.service.stock;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.stock.Stock;
import org.egov.healthnotification.config.HealthNotificationProperties;
import org.egov.healthnotification.util.RequestInfoUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Calls stock service search API to find matching DISPATCHED stock
 * for discrepancy calculation when a RECEIVED event is processed.
 */
@Service
@Slf4j
public class StockSearchService {

    private final ServiceRequestClient serviceRequestClient;
    private final HealthNotificationProperties properties;
    private final ObjectMapper objectMapper;

    @Autowired
    public StockSearchService(ServiceRequestClient serviceRequestClient,
                               HealthNotificationProperties properties,
                               ObjectMapper objectMapper) {
        this.serviceRequestClient = serviceRequestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Finds the matching DISPATCHED stock for a RECEIVED stock event.
     * Uses wayBillNumber (primary) or senderId+receiverId+productVariantId (fallback).
     *
     * @param receivedStock The received stock event
     * @param tenantId      The tenant ID
     * @return The matching dispatched Stock, or null if not found
     */
    public Stock findMatchingDispatchedStock(Stock receivedStock, String tenantId) {
        try {
            // Build search criteria
            Map<String, Object> request = new HashMap<>();
            request.put("RequestInfo", RequestInfoUtil.buildSystemRequestInfo());

            Map<String, Object> stockSearch = new HashMap<>();
            stockSearch.put("tenantId", tenantId);

            // Primary: search by wayBillNumber
            if (receivedStock.getWayBillNumber() != null && !receivedStock.getWayBillNumber().isBlank()) {
                stockSearch.put("wayBillNumber", receivedStock.getWayBillNumber());
                stockSearch.put("transactionType", "DISPATCHED");
            } else {
                // Fallback: search by sender/receiver/product
                // For a RECEIVED stock, the sender is the one who dispatched
                stockSearch.put("senderId", receivedStock.getSenderId());
                stockSearch.put("receiverId", receivedStock.getReceiverId());
                stockSearch.put("productVariantId", receivedStock.getProductVariantId());
                stockSearch.put("transactionType", "DISPATCHED");
            }

            request.put("Stock", stockSearch);

            StringBuilder uri = new StringBuilder(properties.getStockServiceHost())
                    .append(properties.getStockSearchUrl())
                    .append("?tenantId=").append(tenantId)
                    .append("&limit=1&offset=0");

            log.info("Searching for matching dispatched stock: wayBillNumber={}, productVariantId={}",
                    receivedStock.getWayBillNumber(), receivedStock.getProductVariantId());

            JsonNode response = serviceRequestClient.fetchResult(uri, request, JsonNode.class);

            if (response != null && response.has("Stock") && response.get("Stock").isArray()
                    && response.get("Stock").size() > 0) {
                JsonNode stockNode = response.get("Stock").get(0);
                Stock dispatchedStock = objectMapper.treeToValue(stockNode, Stock.class);
                log.info("Found matching dispatched stock: id={}, quantity={}",
                        dispatchedStock.getId(), dispatchedStock.getQuantity());
                return dispatchedStock;
            }

            log.info("No matching dispatched stock found for wayBillNumber={}, productVariantId={}",
                    receivedStock.getWayBillNumber(), receivedStock.getProductVariantId());
            return null;

        } catch (Exception e) {
            log.error("Error searching for matching dispatched stock: {}", e.getMessage(), e);
            return null;
        }
    }
}
