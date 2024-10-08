package org.egov.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.product.ProductVariant;
import org.egov.common.models.product.ProductVariantRequest;
import org.egov.common.service.IdGenService;
import org.egov.product.config.ProductConfiguration;
import org.egov.product.repository.ProductVariantRepository;
import org.egov.product.web.models.Mdms;
import org.egov.product.web.models.ProductVariantSearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.checkRowVersion;
import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.enrichForUpdate;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getSet;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.identifyNullIds;
import static org.egov.common.utils.CommonUtils.includeDeleted;
import static org.egov.common.utils.CommonUtils.lastChangedSince;
import static org.egov.common.utils.CommonUtils.validateEntities;
import static org.egov.common.utils.CommonUtils.validateIds;
import static org.egov.product.config.ServiceConstants.*;

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
        productVariantRepository.save(request.getProductVariant(), productConfiguration.getCreateProductVariantTopic());
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

        productVariantRepository.save(request.getProductVariant(), productConfiguration.getUpdateProductVariantTopic());

        return request.getProductVariant();
    }

    public List<ProductVariant> search(ProductVariantSearchRequest productVariantSearchRequest,
                                       Integer limit,
                                       Integer offset,
                                       String tenantId,
                                       Long lastChangedSince,
                                       Boolean includeDeleted) throws Exception {
        log.info("received request to search product variants");

        List <String> ids = productVariantSearchRequest.getProductVariant().getId();

        List <Mdms> jsonNode = mdmsV2Service.fetchMdmsData(productVariantSearchRequest.getRequestInfo(), tenantId, Boolean.FALSE, ids, limit, offset);

        List<ProductVariant> productVariants = new ArrayList<>();

        jsonNode.forEach(data -> {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                // Convert JsonNode to ProductVariant object
                ProductVariant productVariant = objectMapper.treeToValue(data.getData(), ProductVariant.class);
                productVariants.add(productVariant);
            } catch (Exception e) {
                log.error(e.getMessage());
                throw new CustomException(JSONPATH_ERROR_CODE, JSONPATH_ERROR_MESSAGE);
            }
        });

        List <ProductVariant> filteredProductVarient = productVariants.stream()
                .filter(varient -> {
                    if(productVariantSearchRequest.getProductVariant().getVariation() == null) {
                        return true;
                    }
                    return varient.getVariation().equals(productVariantSearchRequest.getProductVariant().getVariation().toString());})
                .filter(lastChangedSince(lastChangedSince)).filter(includeDeleted(includeDeleted)).collect(Collectors.toList());

        return  filteredProductVarient;
    }
}
