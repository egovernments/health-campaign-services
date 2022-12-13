package org.egov.product.enrichment;

import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.service.IdGenService;
import org.egov.product.web.models.Product;
import org.egov.product.web.models.ProductRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

@Service
@Slf4j
public class ProductEnrichment {
    private final IdGenService idGenService;

    @Autowired
    public ProductEnrichment(IdGenService idGenService) {
        this.idGenService = idGenService;
    }

    public ProductRequest enrichProduct(ProductRequest productRequest) throws Exception {
        // TODO: Do all these in a single loop - UPDATED
        List<String> idList =  getIdsForProducts(productRequest);
        AuditDetails auditDetails = getAuditDetailsForNewProduct(productRequest);
        IntStream.range(0, productRequest.getProduct().size()).forEach(
                i -> {
                    Product product = productRequest.getProduct().get(i);
                    product.setId(idList.get(i));
                    product.setAuditDetails(auditDetails);
                    product.setIsDeleted(false);
                    product.setRowVersion(1);
                }
        );
        return productRequest;
    }

    public ProductRequest enrichUpdateProduct(ProductRequest productRequest) throws Exception {
        // TODO: Do all these in a single loop - UPDATED
        AuditDetails auditDetails = getAuditDetails(productRequest);
        IntStream.range(0, productRequest.getProduct().size()).forEach(
                i -> {
                    Product product = productRequest.getProduct().get(i);
                    product.setAuditDetails(auditDetails);
                }
        );
        return productRequest;
    }


    private List<String> getIdsForProducts(ProductRequest productRequest) throws Exception {
        log.info("Generating IDs using IdGenService");
        return idGenService.getIdList(productRequest.getRequestInfo(),
                productRequest.getProduct().get(0).getTenantId(),
                "product.id", "", productRequest.getProduct().size());
    }

    private AuditDetails getAuditDetailsForNewProduct(ProductRequest productRequest){
        log.info("Generating Audit Details for new products");
        AuditDetails auditDetails = AuditDetails.builder()
                .createdBy(productRequest.getRequestInfo().getUserInfo().getUuid())
                .createdTime(System.currentTimeMillis())
                .lastModifiedBy(productRequest.getRequestInfo().getUserInfo().getUuid())
                .lastModifiedTime(System.currentTimeMillis()).build();
        return auditDetails;
    }

    private AuditDetails getAuditDetails(ProductRequest productRequest){
        log.info("Generating Audit Details for products");
        AuditDetails auditDetails = AuditDetails.builder()
                .lastModifiedBy(productRequest.getRequestInfo().getUserInfo().getUuid())
                .lastModifiedTime(System.currentTimeMillis()).build();
        return auditDetails;
    }
}
