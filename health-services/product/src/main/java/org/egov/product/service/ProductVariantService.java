package org.egov.product.service;

import org.egov.product.util.IdGenService;
import org.egov.product.web.models.ProductVariant;
import org.egov.product.web.models.ProductVariantRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Service
public class ProductVariantService {

    private final IdGenService idGenService;

    @Autowired
    public ProductVariantService(IdGenService idGenService) {

        this.idGenService = idGenService;
    }

    public List<ProductVariant> create(ProductVariantRequest request) {
        Optional<ProductVariant> anyProductVariant = request.getProductVariant()
                .stream().findAny();
        String tenantId = null;
        if (anyProductVariant.isPresent()) {
            tenantId = anyProductVariant.get().getTenantId();
        }
        List<String> idList = idGenService.getIdList(request.getRequestInfo(), tenantId,
                "productVariant", "", request.getProductVariant().size());
        IntStream.range(0, request.getProductVariant().size())
                .forEach(i -> request.getProductVariant().get(i).setId(idList.get(i)));
        return request.getProductVariant();
    }
}
