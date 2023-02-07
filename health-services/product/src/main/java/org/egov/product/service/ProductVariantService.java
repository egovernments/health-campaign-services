package org.egov.product.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.service.IdGenService;
import org.egov.product.config.ProductConfiguration;
import org.egov.product.repository.ProductVariantRepository;
import org.egov.product.web.models.ProductVariant;
import org.egov.product.web.models.ProductVariantRequest;
import org.egov.product.web.models.ProductVariantSearchRequest;
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
import static org.egov.common.utils.CommonUtils.getSet;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.havingTenantId;
import static org.egov.common.utils.CommonUtils.identifyNullIds;
import static org.egov.common.utils.CommonUtils.includeDeleted;
import static org.egov.common.utils.CommonUtils.isSearchByIdOnly;
import static org.egov.common.utils.CommonUtils.lastChangedSince;
import static org.egov.common.utils.CommonUtils.validateEntities;
import static org.egov.common.utils.CommonUtils.validateIds;

@Service
@Slf4j
public class ProductVariantService {

    private final IdGenService idGenService;

    private final ProductService productService;

    private final ProductVariantRepository productVariantRepository;

    private final ProductConfiguration productConfiguration;

    @Autowired
    public ProductVariantService(IdGenService idGenService, ProductService productService,
                                 ProductVariantRepository productVariantRepository,
                                 ProductConfiguration productConfiguration) {
        this.idGenService = idGenService;
        this.productService = productService;
        this.productVariantRepository = productVariantRepository;
        this.productConfiguration = productConfiguration;
    }

    public List<ProductVariant> create(ProductVariantRequest request) throws Exception {
        validateIds(getSet(request.getProductVariant(), "getProductId"),
                productService::validateProductId);
        log.info("Generating IDs using IdGenService");
        List<String> idList = idGenService.getIdList(request.getRequestInfo(),
                getTenantId(request.getProductVariant()),
                "product.variant.id", "", request.getProductVariant().size());
        log.info("IDs generated");
        enrichForCreate(request.getProductVariant(), idList, request.getRequestInfo());
        log.info("Enrichment done");
        productVariantRepository.save(request.getProductVariant(), productConfiguration.getCreateProductVariantTopic());
        log.info("Pushed to kafka");
        return request.getProductVariant();
    }

    public List<ProductVariant> update(ProductVariantRequest request) {
        identifyNullIds(request.getProductVariant());
        validateIds(getSet(request.getProductVariant(), "getProductId"),
                productService::validateProductId);
        Map<String, ProductVariant> pvMap = getIdToObjMap(request.getProductVariant());

        log.info("Checking if already exists");
        List<String> productVariantIds = new ArrayList<>(pvMap.keySet());
        List<ProductVariant> existingProductVariants = productVariantRepository
                .findById(productVariantIds);

        validateEntities(pvMap, existingProductVariants);

        checkRowVersion(pvMap, existingProductVariants);

        log.info("Updating lastModifiedTime and lastModifiedBy");
        enrichForUpdate(pvMap, existingProductVariants, request);

        productVariantRepository.save(request.getProductVariant(), productConfiguration.getUpdateProductVariantTopic());
        log.info("Pushed to kafka");
        return request.getProductVariant();
    }

    public List<ProductVariant> search(ProductVariantSearchRequest productVariantSearchRequest,
                                Integer limit,
                                Integer offset,
                                String tenantId,
                                Long lastChangedSince,
                                Boolean includeDeleted) throws Exception {

        if (isSearchByIdOnly(productVariantSearchRequest.getProductVariant())) {
            List<String> ids = productVariantSearchRequest.getProductVariant().getId();
            return productVariantRepository.findById(ids, includeDeleted).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }
        return productVariantRepository.find(productVariantSearchRequest.getProductVariant(),
                limit, offset, tenantId, lastChangedSince, includeDeleted);
    }
}
