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

    private void enrichProductWithIds(ProductRequest productRequest) throws Exception {
        log.info("Generating IDs using IdGenService");
        List<String> idList = idGenService.getIdList(productRequest.getRequestInfo(), productRequest.getProduct().get(0).getTenantId(),
                "product.id", "", productRequest.getProduct().size());
        log.info("IDs generated");
        IntStream.range(0, productRequest.getProduct().size())
                .forEach(i -> productRequest.getProduct().get(i).setId(idList.get(i)));
    }

    private void enrichProductWithAuditDetails(ProductRequest productRequest){
//        AuditDetails auditDetails = AuditDetails.builder()
//                .createdBy(productRequest.getRequestInfo().getUserInfo())
//                .createdTime(System.currentTimeMillis())
//                .lastModifiedBy(deathRegistrationRequest.getRequestInfo().getUserInfo().getUuid())
//                .lastModifiedTime(System.currentTimeMillis()).build();
    }

    public ProductRequest enrichProduct(ProductRequest productRequest) throws Exception {
        enrichProductWithIds(productRequest);
        return  productRequest;
    }


}
