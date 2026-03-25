package org.egov.fhirtransformer.mapping.requestBuilder;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.product.*;
import org.egov.fhirtransformer.service.ApiIntegrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Service responsible for transforming FHIR InventoryItem–derived
 * {@link ProductVariant} data into DIGIT Product service requests.
 */

@Service
public class InventoryItemToProductVariant {

    @Autowired
    private ApiIntegrationService apiIntegrationService;

    @Autowired
    private GenericCreateOrUpdateService genericCreateOrUpdateService;

    @Value("${product.variant.create.url}")
    private String productVariantCreateUrl;

    @Value("${product.variant.update.url}")
    private String productVariantUpdateUrl;

    private RequestInfo requestInfo;

    /**
     * Transforms and persists ProductVariants derived from InventoryItems.
     * @param productVariantMap map of ProductVariant ID to ProductVariant data;
     *                          may be empty but not {@code null}
     * @return map containing processing metrics
     * @throws Exception if transformation or API invocation fails
     */
    public HashMap<String, Integer> transformInventoryItemToProductVariant(HashMap<String, ProductVariant> productVariantMap, RequestInfo requestInfo) throws Exception {

        this.requestInfo=requestInfo;
        // Use the generic overloaded process: provide fetchExistingIds, create and update adapters
        return genericCreateOrUpdateService.process(productVariantMap,
                this::fetchExistingProductVariantIds,
                this::createProductVariants,
                this::updateProductVariants,
                productVariantCreateUrl,
                productVariantUpdateUrl,
                requestInfo,
                "Error in Transforming InventoryItem To ProductVariant");
    }

    // Adapter: fetch existing IDs as flat list
    public List<String> fetchExistingProductVariantIds(List<String> productVariantIds) throws Exception {
        try{
            URLParams urlParams = apiIntegrationService.formURLParams(productVariantIds);
            ProductVariantSearch productVariantSearch = new ProductVariantSearch();
            productVariantSearch.setId(productVariantIds);

            ProductVariantSearchRequest productVariantSearchRequest = new ProductVariantSearchRequest();
            productVariantSearchRequest.setRequestInfo(this.requestInfo);
            productVariantSearchRequest.setProductVariant(productVariantSearch);

            ProductVariantResponse productVariantResponse = apiIntegrationService.fetchAllProductVariants(urlParams, productVariantSearchRequest);

            if (productVariantResponse.getProductVariant() == null){
                return new ArrayList<>();
            }
            List<String> existingIds = new ArrayList<>();
            for (ProductVariant productVariant : productVariantResponse.getProductVariant()) {
                existingIds.add(productVariant.getId());
            }
            return existingIds;
        } catch (Exception e){
            throw new Exception("Error in fetchExisting productVariant: " + e.getMessage());
        }
    }

    // Adapter: create list of ProductVariant using provided createUrl
    public void createProductVariants(List<ProductVariant> toCreate, String createUrl) throws Exception {
        try{
            if (toCreate == null || toCreate.isEmpty()) return;
            ProductVariantRequest productVariantRequest = new ProductVariantRequest();
            productVariantRequest.setRequestInfo(this.requestInfo);
            productVariantRequest.setProductVariant(toCreate);
            productVariantRequest.setApiOperation(ApiOperation.CREATE);
            apiIntegrationService.sendRequestToAPI(productVariantRequest, createUrl);
        } catch (Exception e) {
            throw new Exception("Error in createProductVariants: " + e.getMessage());
        }
    }

    // Adapter: update list of ProductVariant using provided updateUrl
    public void updateProductVariants(List<ProductVariant> toUpdate, String updateUrl) throws Exception {
        try{
            if (toUpdate == null || toUpdate.isEmpty()) return;
            ProductVariantRequest productVariantRequest = new ProductVariantRequest();
            productVariantRequest.setRequestInfo(this.requestInfo);
            productVariantRequest.setProductVariant(toUpdate);
            productVariantRequest.setApiOperation(ApiOperation.UPDATE);
            apiIntegrationService.sendRequestToAPI(productVariantRequest, updateUrl);
        } catch (Exception e) {
            throw new Exception("Error in updateProductVariants: " + e.getMessage());
        }
    }

    // ...existing legacy methods left for compatibility (not used by new flow)...
}
