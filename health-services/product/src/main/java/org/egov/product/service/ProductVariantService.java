package org.egov.product.service;

import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.service.IdGenService;
import org.egov.product.repository.ProductVariantRepository;
import org.egov.product.web.models.ApiOperation;
import org.egov.product.web.models.ProductVariant;
import org.egov.product.web.models.ProductVariantRequest;
import org.egov.product.web.models.ProductVariantSearch;
import org.egov.product.web.models.ProductVariantSearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
        log.info("Generating IDs using IdGenService");
        List<String> idList = idGenService.getIdList(request.getRequestInfo(), getTenantId(request.getProductVariant()),
                "product.variant.id", "", request.getProductVariant().size());
        log.info("IDs generated");
        AuditDetails auditDetails = createAuditDetailsForCreate(request.getRequestInfo());
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

    public List<ProductVariant> update(ProductVariantRequest request) {
        validateProductId(request);
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

    private void validateProductId(ProductVariantRequest request) {
        Set<String> productIds = request.getProductVariant().stream()
                .map(ProductVariant::getProductId)
                .collect(Collectors.toSet());
        List<String> validProductIds = productService.validateProductId(new ArrayList<>(productIds));
        if (validProductIds.size() != productIds.size()) {
            List<String> invalidProductIds = new ArrayList<>(productIds);
            invalidProductIds.removeAll(validProductIds);
            log.error("Invalid productIds");
            throw new CustomException("INVALID_PRODUCT_ID", invalidProductIds.toString());
        }
    }

    private AuditDetails createAuditDetailsForCreate(RequestInfo requestInfo) {
        Long time = System.currentTimeMillis();
        return AuditDetails.builder()
                .createdBy(requestInfo.getUserInfo().getUuid())
                .createdTime(time)
                .lastModifiedBy(requestInfo.getUserInfo().getUuid())
                .lastModifiedTime(time).build();
    }

    private AuditDetails getAuditDetailsForUpdate(AuditDetails existingAuditDetails, String uuid) {
        log.info("Generating Audit Details for products");

        return AuditDetails.builder()
                .createdBy(existingAuditDetails.getCreatedBy())
                .createdTime(existingAuditDetails.getCreatedTime())
                .lastModifiedBy(uuid)
                .lastModifiedTime(System.currentTimeMillis()).build();
    }

    private String getTenantId(List<ProductVariant> projectStaffs) {
        String tenantId = null;
        Optional<ProductVariant> anyProjectStaff = projectStaffs.stream().findAny();
        if (anyProjectStaff.isPresent()) {
            tenantId = anyProjectStaff.get().getTenantId();
        }
        log.info("Using tenantId {}",tenantId);
        return tenantId;
    }

    private void checkRowVersion(Map<String, ProductVariant> idToPvMap,
                                        List<ProductVariant> existingProductVariants) {
        Set<String> rowVersionMismatch = existingProductVariants.stream()
                .filter(existingPv -> !Objects.equals(existingPv.getRowVersion(),
                        idToPvMap.get(existingPv.getId()).getRowVersion()))
                .map(ProductVariant::getId).collect(Collectors.toSet());
        if (!rowVersionMismatch.isEmpty()) {
            throw new CustomException("ROW_VERSION_MISMATCH", rowVersionMismatch.toString());
        }
    }


    public List<ProductVariant> search(ProductVariantSearchRequest productVariantSearchRequest,
                                Integer limit,
                                Integer offset,
                                String tenantId,
                                Long lastChangedSince,
                                Boolean includeDeleted) throws Exception {

        if (isSearchByIdOnly(productVariantSearchRequest)) {
            List<String> ids = new ArrayList<>();
            ids.add(productVariantSearchRequest.getProductVariant().getId());
            return productVariantRepository.findById(ids);
        }
        return productVariantRepository.find(productVariantSearchRequest.getProductVariant(),
                limit, offset, tenantId, lastChangedSince, includeDeleted);
    }

    private boolean isSearchByIdOnly(ProductVariantSearchRequest productVariantSearchRequest) {
        ProductVariantSearch productVariantSearch = ProductVariantSearch.builder()
                .id(productVariantSearchRequest.getProductVariant()
                .getId()).build();
        String productSearchHash = productVariantSearch.toString();
        String hashFromRequest = productVariantSearchRequest.getProductVariant().toString();
        return productSearchHash.equals(hashFromRequest);
    }
}
