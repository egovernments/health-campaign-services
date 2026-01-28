package org.egov.product.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.product.Product;
import org.egov.common.models.product.ProductRequest;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.product.config.ProductConfiguration;
import org.egov.product.repository.ProductRepository;
import org.egov.product.web.models.ProductSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.checkRowVersion;
import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.enrichForUpdate;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.havingTenantId;
import static org.egov.common.utils.CommonUtils.identifyNullIds;
import static org.egov.common.utils.CommonUtils.includeDeleted;
import static org.egov.common.utils.CommonUtils.isSearchByIdOnly;
import static org.egov.common.utils.CommonUtils.lastChangedSince;
import static org.egov.common.utils.CommonUtils.validateEntities;

@Service
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;

    private final IdGenService idGenService;

    private final ProductConfiguration productConfiguration;

    @Autowired
    public ProductService(ProductRepository productRepository, IdGenService idGenService,
                          ProductConfiguration productConfiguration) {
        this.productRepository = productRepository;
        this.idGenService = idGenService;
        this.productConfiguration = productConfiguration;
    }

    /**
     * Validates the product IDs against the database.
     *
     * @param tenantId   The tenant ID to validate against.
     * @param productIds The list of product IDs to validate.
     * @return A list of valid product IDs.
     * @throws InvalidTenantIdException If the tenant ID is invalid.
     */
    public List<String> validateProductId( String tenantId , List<String> productIds) throws InvalidTenantIdException {
        return productRepository.validateIds( tenantId, productIds, "id");
    }

    public List<Product> create(ProductRequest productRequest) throws Exception {
        log.info("Enrichment products started");
        log.info("generating ids for products");
        List<String> idList =  idGenService.getIdList(productRequest.getRequestInfo(),
                getTenantId(productRequest.getProduct()),
                "product.id", "", productRequest.getProduct().size());

        log.info("enriching products");
        enrichForCreate(productRequest.getProduct(), idList, productRequest.getRequestInfo());

        log.info("saving products");
        productRepository.save(productRequest.getProduct(), productConfiguration.getCreateProductTopic());
        return productRequest.getProduct();
    }

    public List<Product> update(ProductRequest productRequest) throws Exception {
        identifyNullIds(productRequest.getProduct());
        // fetch tenantId from the product
        String tenantId= CommonUtils.getTenantId(productRequest.getProduct());
        Map<String, Product> pMap = getIdToObjMap(productRequest.getProduct());

        log.info("checking if product already exists");
        List<String> productIds = new ArrayList<>(pMap.keySet());
        // find products by id
        List<Product> existingProducts = productRepository.findById(tenantId, productIds);

        log.info("validate entities for products");
        validateEntities(pMap, existingProducts);

        log.info("checking row version for products");
        checkRowVersion(pMap, existingProducts);

        log.info("updating lastModifiedTime and lastModifiedBy");
        enrichForUpdate(pMap, existingProducts, productRequest);

        log.info("saving updated products");
        productRepository.save(productRequest.getProduct(), productConfiguration.getUpdateProductTopic());
        return productRequest.getProduct();
    }

    public List<Product> search(ProductSearchRequest productSearchRequest,
                                Integer limit,
                                Integer offset,
                                String tenantId,
                                Long lastChangedSince,
                                Boolean includeDeleted) throws Exception {

        log.info("received request to search product");

        if (isSearchByIdOnly(productSearchRequest.getProduct())) {
            log.info("searching product by id");
            List<String> ids = productSearchRequest.getProduct().getId();
            log.info("fetching product with ids: {}", ids);
            return productRepository.findById( tenantId, ids, includeDeleted).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }
        log.info("searching product using criteria");
        return productRepository.find(productSearchRequest.getProduct(), limit,
                offset, tenantId, lastChangedSince, includeDeleted);
    }
}
