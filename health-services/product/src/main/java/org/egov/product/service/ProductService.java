package org.egov.product.service;

import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.service.IdGenService;
import org.egov.product.repository.ProductRepository;
import org.egov.product.web.models.ApiOperation;
import org.egov.product.web.models.Product;
import org.egov.product.web.models.ProductRequest;
import org.egov.product.web.models.ProductSearchRequest;
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
public class ProductService {

    private final ProductRepository productRepository;

    private final IdGenService idGenService;

    @Autowired
    public ProductService(ProductRepository productRepository, IdGenService idGenService) {
        this.productRepository = productRepository;
        this.idGenService = idGenService;
    }

    public List<String> validateProductId(List<String> productIds) {
        return productRepository.validateProductId(productIds);
    }

    public List<Product> create(ProductRequest productRequest) throws Exception {
        List<String> productIds = productRequest.getProduct().stream()
                .map(Product::getId)
                .collect(Collectors.toList());
        log.info("Checking if already exists");
        List<String> inValidProductIds = productRepository.validateProductId(productIds);
        if (!inValidProductIds.isEmpty()) {
            log.info("Products {} already present in DB", inValidProductIds);
            throw new CustomException("PRODUCT_ALREADY_EXISTS", inValidProductIds.toString());
        }
        log.info("Enrichment products started");

        List<String> idList =  idGenService.getIdList(productRequest.getRequestInfo(), getTenantId(productRequest.getProduct()),
                "product.id", "", productRequest.getProduct().size());
        AuditDetails auditDetails = getAuditDetailsForCreate(productRequest);
        IntStream.range(0, productRequest.getProduct().size()).forEach(
                i -> {
                    Product product = productRequest.getProduct().get(i);
                    product.setId(idList.get(i));
                    product.setAuditDetails(auditDetails);
                    product.setIsDeleted(false);
                    product.setRowVersion(1);
                }
        );
        productRepository.save(productRequest.getProduct(), "save-product-topic");
        return productRequest.getProduct();
    }

    public List<Product> update(ProductRequest productRequest) throws Exception {
        Map<String, Product> pMap =
                productRequest.getProduct().stream().collect(Collectors.toMap(Product::getId, item -> item));
        List<String> productIds = new ArrayList<>(pMap.keySet());

        log.info("Checking if already exists");
        List<Product> existingProducts = productRepository.findById(productIds);
        if (existingProducts.size() != productRequest.getProduct().size()) {
            List<String> existingProductIds = existingProducts.stream().map(Product::getId).collect(Collectors.toList());
            List<String> invalidProductIds = pMap.keySet().stream().filter(id -> !existingProductIds.contains(id))
                    .collect(Collectors.toList());
            throw new CustomException("INVALID_PRODUCT", invalidProductIds.toString());
        }
        checkRowVersion(pMap, existingProducts);

        IntStream.range(0, existingProducts.size()).forEach(i -> {
            Product p = pMap.get(existingProducts.get(i).getId());
            if (productRequest.getApiOperation().equals(ApiOperation.DELETE)) {
                p.setIsDeleted(true);
            }
            p.setRowVersion(p.getRowVersion() + 1);
            AuditDetails existingAuditDetails = existingProducts.get(i).getAuditDetails();
            p.setAuditDetails(getAuditDetailsForUpdate(existingAuditDetails,
                    productRequest.getRequestInfo().getUserInfo().getUuid()));
        });

        productRepository.save(productRequest.getProduct(), "update-product-topic");
        return productRequest.getProduct();
    }

    public List<Product> search(ProductSearchRequest productSearchRequest,
                                Integer limit,
                                Integer offset,
                                String tenantId,
                                Long lastChangedSince,
                                Boolean includeDeleted) throws Exception {
//        ProductSearch search = ProductSearch.builder().id("P-2022-12-20-000043").build();
//        System.out.println(search.toString().hashCode());
//        System.out.println(productSearchRequest.getProduct().toString().hashCode());
        List<Product> products = productRepository.find(productSearchRequest.getProduct(), limit,
                offset, tenantId, lastChangedSince, includeDeleted);
        if (products.isEmpty()) {
            throw new CustomException("NO_RESULT", "No records found for the given search criteria");
        }
        return products;
    }

    private void checkRowVersion(Map<String, Product> idToPMap,
                                 List<Product> existingProducts) {
        Set<String> rowVersionMismatch = existingProducts.stream()
                .filter(existingPv -> !Objects.equals(existingPv.getRowVersion(),
                        idToPMap.get(existingPv.getId()).getRowVersion()))
                .map(Product::getId).collect(Collectors.toSet());
        if (!rowVersionMismatch.isEmpty()) {
            throw new CustomException("ROW_VERSION_MISMATCH", rowVersionMismatch.toString());
        }
    }

    private String getTenantId(List<Product> products) {
        String tenantId = null;
        Optional<Product> anyProduct = products.stream().findAny();
        if (anyProduct.isPresent()) {
            tenantId = anyProduct.get().getTenantId();
        }
        log.info("Using tenantId {}",tenantId);
        return tenantId;
    }

    private AuditDetails getAuditDetailsForCreate(ProductRequest productRequest) {
        log.info("Generating Audit Details for new products");
        return AuditDetails.builder()
                .createdBy(productRequest.getRequestInfo().getUserInfo().getUuid())
                .createdTime(System.currentTimeMillis())
                .lastModifiedBy(productRequest.getRequestInfo().getUserInfo().getUuid())
                .lastModifiedTime(System.currentTimeMillis()).build();
    }

    private AuditDetails getAuditDetailsForUpdate(AuditDetails existingAuditDetails, String uuid) {
        log.info("Generating Audit Details for products");

        return AuditDetails.builder()
                .createdBy(existingAuditDetails.getCreatedBy())
                .createdTime(existingAuditDetails.getCreatedTime())
                .lastModifiedBy(uuid)
                .lastModifiedTime(System.currentTimeMillis()).build();
    }
}
