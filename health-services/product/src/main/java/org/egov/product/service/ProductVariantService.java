package org.egov.product.service;

import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.producer.Producer;
import org.egov.common.service.IdGenService;
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

    private final Producer producer;

    private final ProductService productService;

    @Autowired
    public ProductVariantService(IdGenService idGenService, Producer producer, ProductService productService) {
        this.idGenService = idGenService;
        this.producer = producer;
        this.productService = productService;
    }

    public List<ProductVariant> create(ProductVariantRequest request) throws Exception {
        List<String> productIds = request.getProductVariant().stream()
                .map(ProductVariant::getProductId)
                .collect(Collectors.toList());
        List<String> validProductIds = productService.validateProductId(productIds);
        if (validProductIds.size() != productIds.size()) {
            List<String> invalidProductIds = new ArrayList<>(productIds);
            invalidProductIds.removeAll(validProductIds);
            log.error("Invalid ProductIds");
            throw new CustomException("INVALID_PRODUCT_ID", invalidProductIds.toString());
        }
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
        producer.push("save-product-variant-persister-topic", request);
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
}
