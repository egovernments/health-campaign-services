package org.egov.product.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.product.ProductVariant;
import org.egov.common.models.product.ProductVariantRequest;
import org.egov.common.service.IdGenService;
import org.egov.product.config.ProductConfiguration;
import org.egov.product.repository.ProductVariantRepository;
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
import static org.egov.product.Constants.PRODUCT_VARIANT_CACHE_FIELD;

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
        log.info("validating product ids");
        validateIds(getSet(request.getProductVariant(), "getProductId"),
                productService::validateProductId);
        log.info("generating IDs using IdGenService");
        List<String> idList = idGenService.getIdList(request.getRequestInfo(),
                getTenantId(request.getProductVariant()),
                "product.variant.id", "", request.getProductVariant().size());
        log.info("ids generated");
        enrichForCreate(request.getProductVariant(), idList, request.getRequestInfo());
        log.info("Enrichment done");
        log.info("saving the product variants");
        productVariantRepository.save(request, productConfiguration.getCreateProductVariantTopic(), PRODUCT_VARIANT_CACHE_FIELD);
        log.info("saved product variants");
        return request.getProductVariant();
    }

    public List<ProductVariant> update(ProductVariantRequest request) {
        identifyNullIds(request.getProductVariant());

        log.info("validating product ids");
        validateIds(getSet(request.getProductVariant(), "getProductId"),
                productService::validateProductId);
        Map<String, ProductVariant> pvMap = getIdToObjMap(request.getProductVariant());

        log.info("checking if already exists");
        List<String> productVariantIds = new ArrayList<>(pvMap.keySet());
        List<ProductVariant> existingProductVariants = productVariantRepository
                .findById(productVariantIds);

        log.info("checking validate entities for product variants");
        validateEntities(pvMap, existingProductVariants);

        log.info("checking version product variants");
        checkRowVersion(pvMap, existingProductVariants);

        log.info("updating product variants lastModifiedTime and lastModifiedBy");
        enrichForUpdate(pvMap, existingProductVariants, request);

        productVariantRepository.save(request, productConfiguration.getUpdateProductVariantTopic(), PRODUCT_VARIANT_CACHE_FIELD);

        return request.getProductVariant();
    }

    public List<ProductVariant> search(ProductVariantSearchRequest productVariantSearchRequest,
                                       Integer limit,
                                       Integer offset,
                                       String tenantId,
                                       Long lastChangedSince,
                                       Boolean includeDeleted) throws Exception {
        log.info("received request to search product variants");

        if (isSearchByIdOnly(productVariantSearchRequest.getProductVariant())) {
            log.info("searching product variants by id");
            List<String> ids = productVariantSearchRequest.getProductVariant().getId();
            log.info("fetching product variants with ids: {}", ids);
            return productVariantRepository.findById(ids, includeDeleted).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }
        log.info("searching product variants using criteria");
        return productVariantRepository.find(productVariantSearchRequest.getProductVariant(),
                limit, offset, tenantId, lastChangedSince, includeDeleted);
    }
}
