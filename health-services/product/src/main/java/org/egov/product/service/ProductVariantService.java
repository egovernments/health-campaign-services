package org.egov.product.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.product.Product;
import org.egov.common.models.product.ProductVariant;
import org.egov.common.models.product.ProductVariantRequest;
import org.egov.common.service.IdGenService;
import org.egov.product.config.ProductConfiguration;
import org.egov.product.repository.ProductVariantRepository;
import org.egov.product.web.models.ProductVariantSearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
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
import static org.egov.product.service.MdmsV2Service.JSONPATH_ERROR_CODE;
import static org.egov.product.service.MdmsV2Service.JSONPATH_ERROR_MESSAGE;

@Service
@Slf4j
public class ProductVariantService {

    private final IdGenService idGenService;

    private final ProductService productService;

    private final ProductVariantRepository productVariantRepository;

    private final ProductConfiguration productConfiguration;

    private final MdmsV2Service mdmsV2Service;

    private final ObjectMapper objectMapper;

    @Autowired
    public ProductVariantService(IdGenService idGenService, ProductService productService,
                                 ProductVariantRepository productVariantRepository,
                                 ProductConfiguration productConfiguration, MdmsV2Service mdmsV2Service, ObjectMapper objectMapper) {
        this.idGenService = idGenService;
        this.productService = productService;
        this.productVariantRepository = productVariantRepository;
        this.productConfiguration = productConfiguration;
        this.mdmsV2Service = mdmsV2Service;
        this.objectMapper = objectMapper;
    }

    public List<ProductVariant> create(ProductVariantRequest request) throws Exception {
//        log.info("validating product ids");
//        validateIds(getSet(request.getProductVariant(), "getProductId"),
//                productService::validateProductId);
//        log.info("generating IDs using IdGenService");
//        List<String> idList = idGenService.getIdList(request.getRequestInfo(),
//                getTenantId(request.getProductVariant()),
//                "product.variant.id", "", request.getProductVariant().size());
//        log.info("ids generated");
//        enrichForCreate(request.getProductVariant(), idList, request.getRequestInfo());
//        log.info("Enrichment done");
//        log.info("saving the product variants");
//        productVariantRepository.save(request.getProductVariant(), productConfiguration.getCreateProductVariantTopic());
//        log.info("saved product variants");
//        return request.getProductVariant();
        return Collections.emptyList();
    }

    public List<ProductVariant> update(ProductVariantRequest request) {
//        identifyNullIds(request.getProductVariant());
//
//        log.info("validating product ids");
//        validateIds(getSet(request.getProductVariant(), "getProductId"),
//                productService::validateProductId);
//        Map<String, ProductVariant> pvMap = getIdToObjMap(request.getProductVariant());
//
//        log.info("checking if already exists");
//        List<String> productVariantIds = new ArrayList<>(pvMap.keySet());
//        List<ProductVariant> existingProductVariants = productVariantRepository
//                .findById(productVariantIds);
//
//        log.info("checking validate entities for product variants");
//        validateEntities(pvMap, existingProductVariants);
//
//        log.info("checking version product variants");
//        checkRowVersion(pvMap, existingProductVariants);
//
//        log.info("updating product variants lastModifiedTime and lastModifiedBy");
//        enrichForUpdate(pvMap, existingProductVariants, request);
//
//        productVariantRepository.save(request.getProductVariant(), productConfiguration.getUpdateProductVariantTopic());
//
//        return request.getProductVariant();

        return Collections.emptyList();
    }

    public List<ProductVariant> search(ProductVariantSearchRequest productVariantSearchRequest,
                                       Integer limit,
                                       Integer offset,
                                       String tenantId,
                                       Long lastChangedSince,
                                       Boolean includeDeleted) throws Exception {
        log.info("received request to search product variants");

//        if (isSearchByIdOnly(productVariantSearchRequest.getProductVariant())) {
//            log.info("searching product variants by id");
//            List<String> ids = productVariantSearchRequest.getProductVariant().getId();
//            log.info("fetching product variants with ids: {}", ids);
//            return productVariantRepository.findById(ids, includeDeleted).stream()
//                    .filter(lastChangedSince(lastChangedSince))
//                    .filter(havingTenantId(tenantId))
//                    .filter(includeDeleted(includeDeleted))
//                    .collect(Collectors.toList());
//        }
//        log.info("searching product variants using criteria");
//        return productVariantRepository.find(productVariantSearchRequest.getProductVariant(),
//                limit, offset, tenantId, lastChangedSince, includeDeleted);

        Object jsonNode = mdmsV2Service.fetchMdmsData(productVariantSearchRequest.getRequestInfo(), tenantId, Boolean.FALSE);
        log.info(jsonNode.toString());
        List<ProductVariant> productVariants = Collections.emptyList();
        final String jsonPathForAssumption = "$.HCM-Product.ProductVariants.*";

        try {
            log.info(jsonPathForAssumption);
            productVariants = JsonPath.read(jsonNode, jsonPathForAssumption);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new CustomException(JSONPATH_ERROR_CODE, JSONPATH_ERROR_MESSAGE);
        }

        return productVariants;
    }
}
