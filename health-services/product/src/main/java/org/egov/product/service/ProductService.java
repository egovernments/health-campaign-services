package org.egov.product.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.service.IdGenService;
import org.egov.product.config.ProductConfiguration;
import org.egov.product.repository.ProductRepository;
import org.egov.product.web.models.Product;
import org.egov.product.web.models.ProductRequest;
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

    public List<String> validateProductId(List<String> productIds) {
        return productRepository.validateIds(productIds, "id");
    }

    public List<Product> create(ProductRequest productRequest) throws Exception {
        log.info("Enrichment products started");
        List<String> idList =  idGenService.getIdList(productRequest.getRequestInfo(),
                getTenantId(productRequest.getProduct()),
                "product.id", "", productRequest.getProduct().size());
        enrichForCreate(productRequest.getProduct(), idList, productRequest.getRequestInfo());
        productRepository.save(productRequest.getProduct(), productConfiguration.getCreateProductTopic());
        return productRequest.getProduct();
    }

    public List<Product> update(ProductRequest productRequest) throws Exception {
        identifyNullIds(productRequest.getProduct());
        Map<String, Product> pMap = getIdToObjMap(productRequest.getProduct());

        log.info("Checking if already exists");
        List<String> productIds = new ArrayList<>(pMap.keySet());
        List<Product> existingProducts = productRepository.findById(productIds);

        validateEntities(pMap, existingProducts);

        checkRowVersion(pMap, existingProducts);

        log.info("Updating lastModifiedTime and lastModifiedBy");
        enrichForUpdate(pMap, existingProducts, productRequest);

        productRepository.save(productRequest.getProduct(), productConfiguration.getUpdateProductTopic());
        return productRequest.getProduct();
    }

    public List<Product> search(ProductSearchRequest productSearchRequest,
                                Integer limit,
                                Integer offset,
                                String tenantId,
                                Long lastChangedSince,
                                Boolean includeDeleted) throws Exception {
        if (isSearchByIdOnly(productSearchRequest.getProduct())) {
            List<String> ids = new ArrayList<>();
            ids.add(productSearchRequest.getProduct().getId());
            return productRepository.findById(ids, includeDeleted).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }
        return productRepository.find(productSearchRequest.getProduct(), limit,
                offset, tenantId, lastChangedSince, includeDeleted);
    }
}
