package org.egov.product.service;

import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.service.IdGenService;
import org.egov.product.repository.ProductVariantRepository;
import org.egov.product.web.models.ApiOperation;
import org.egov.product.web.models.ProductVariant;
import org.egov.product.web.models.ProductVariantRequest;
import org.egov.product.web.models.ProductVariantSearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.egov.product.util.CommonUtils.checkRowVersion;
import static org.egov.product.util.CommonUtils.enrichForCreate;
import static org.egov.product.util.CommonUtils.getAuditDetailsForUpdate;
import static org.egov.product.util.CommonUtils.getSet;
import static org.egov.product.util.CommonUtils.getTenantId;
import static org.egov.product.util.CommonUtils.isSearchByIdOnly;
import static org.egov.product.util.CommonUtils.validateIds;

@Service
@Slf4j
public class ProductVariantService {

    private final IdGenService idGenService;

    private final ProductService productService;

    private final ProductVariantRepository productVariantRepository;

    @Autowired
    public ProductVariantService(IdGenService idGenService, ProductService productService, ProductVariantRepository productVariantRepository) {
        this.idGenService = idGenService;
        this.productService = productService;
        this.productVariantRepository = productVariantRepository;
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
        productVariantRepository.save(request.getProductVariant(), "save-product-variant-topic");
        log.info("Pushed to kafka");
        return request.getProductVariant();
    }

    public List<ProductVariant> update(ProductVariantRequest request) {
        validateIds(getSet(request.getProductVariant(), "getProductId"),
                productService::validateProductId);
        Map<String, ProductVariant> pvMap =
                request.getProductVariant().stream()
                        .collect(Collectors.toMap(ProductVariant::getId, item -> item));
        List<String> productVariantIds = new ArrayList<>(pvMap.keySet());

        log.info("Checking existing product variants");
        List<ProductVariant> existingProductVariants = productVariantRepository
                .findById(productVariantIds);

        if (request.getProductVariant().size() != existingProductVariants.size()) {
            List<String> existingProductVariantIds = existingProductVariants.stream().map(ProductVariant::getId).collect(Collectors.toList());
            List<String> invalidProductVariantIds = pvMap.keySet().stream().filter(id -> !existingProductVariantIds.contains(id))
                    .collect(Collectors.toList());
            log.error("Invalid product variants");
            throw new CustomException("INVALID_PRODUCT_VARIANT", invalidProductVariantIds.toString());
        }

        checkRowVersion(pvMap, existingProductVariants);

        log.info("Updating lastModifiedTime and lastModifiedBy");
        IntStream.range(0, existingProductVariants.size()).forEach(i -> {
            ProductVariant p = pvMap.get(existingProductVariants.get(i).getId());
            if (request.getApiOperation().equals(ApiOperation.DELETE)) {
                p.setIsDeleted(true);
            }
            p.setRowVersion(p.getRowVersion() + 1);
            AuditDetails existingAuditDetails = existingProductVariants.get(i).getAuditDetails();
            p.setAuditDetails(getAuditDetailsForUpdate(existingAuditDetails,
                    request.getRequestInfo().getUserInfo().getUuid()));
        });

        productVariantRepository.save(request.getProductVariant(), "update-product-variant-topic");
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
            List<String> ids = new ArrayList<>();
            ids.add(productVariantSearchRequest.getProductVariant().getId());
            return productVariantRepository.findById(ids);
        }
        return productVariantRepository.find(productVariantSearchRequest.getProductVariant(),
                limit, offset, tenantId, lastChangedSince, includeDeleted);
    }
}
