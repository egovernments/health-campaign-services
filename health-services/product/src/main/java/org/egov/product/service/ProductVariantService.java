package org.egov.product.service;

import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.service.IdGenService;
import org.egov.product.repository.ProductVariantRepository;
import org.egov.product.web.models.ProductVariant;
import org.egov.product.web.models.ProductVariantRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        validateProductId(request);
        Optional<ProductVariant> anyProductVariant = request.getProductVariant()
                .stream().findAny();
        String tenantId = null;
        if (anyProductVariant.isPresent()) {
            tenantId = anyProductVariant.get().getTenantId();
        }
        log.info("Generating IDs using IdGenService");
        List<String> idList = idGenService.getIdList(request.getRequestInfo(), tenantId,
                "product.variant.id", "", request.getProductVariant().size());
        log.info("IDs generated");
        AuditDetails auditDetails = createAuditDetailsForInsert(request.getRequestInfo());
        IntStream.range(0, request.getProductVariant().size())
                .forEach(i -> {
                    final ProductVariant productVariant = request.getProductVariant().get(i);
                    productVariant.setId(idList.get(i));
                    productVariant.setAuditDetails(auditDetails);
                    productVariant.setRowVersion(1);
                    productVariant.setIsDeleted(Boolean.FALSE);
                });
        log.info("Enrichment done");
        productVariantRepository.save(request.getProductVariant(), "save-product-variant-topic");
        log.info("Pushed to kafka");
        return request.getProductVariant();
    }

    private AuditDetails createAuditDetailsForInsert(RequestInfo requestInfo) {
        return AuditDetails.builder()
                .createdBy(requestInfo.getUserInfo().getUuid())
                .lastModifiedBy(requestInfo.getUserInfo().getUuid())
                .createdTime(System.currentTimeMillis())
                .lastModifiedTime(System.currentTimeMillis())
                .build();

    }

    public List<ProductVariant> update(ProductVariantRequest request) {
        validateProductId(request);

        log.info("Checking existing product variants");
        List<ProductVariant> existingProductVariants = productVariantRepository
                .findById(request.getProductVariant().stream()
                .map(ProductVariant::getId).collect(Collectors.toList()));

        if (request.getProductVariant().size() != existingProductVariants.size()) {
            List<ProductVariant> invalidProductVariants = new ArrayList<>(request.getProductVariant());
            invalidProductVariants.removeAll(existingProductVariants);
            log.error("Invalid product variants");
            throw new CustomException("INVALID_PRODUCT_VARIANT", invalidProductVariants.toString());
        }

        log.info("Updating lastModifiedTime and lastModifiedBy");
        request.getProductVariant().forEach(productVariant -> {
            productVariant.getAuditDetails().setLastModifiedTime(System.currentTimeMillis());
            productVariant.getAuditDetails().setLastModifiedBy(request.getRequestInfo().getUserInfo().getUuid());
            productVariant.setRowVersion(productVariant.getRowVersion() + 1);
        });
        productVariantRepository.save(request.getProductVariant(), "update-product-variant-topic");
        log.info("Pushed to kafka");
        return request.getProductVariant();
    }

    private void validateProductId(ProductVariantRequest request) {
        List<String> productIds = request.getProductVariant().stream()
                .map(ProductVariant::getProductId)
                .collect(Collectors.toList());
        List<String> validProductIds = productService.validateProductId(productIds);
        if (validProductIds.size() != productIds.size()) {
            List<String> invalidProductIds = new ArrayList<>(productIds);
            invalidProductIds.removeAll(validProductIds);
            log.error("Invalid productIds");
            throw new CustomException("INVALID_PRODUCT_ID", invalidProductIds.toString());
        }
    }
}
