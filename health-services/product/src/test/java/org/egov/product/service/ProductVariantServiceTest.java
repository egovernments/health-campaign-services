package org.egov.product.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.service.IdGenService;
import org.egov.product.helper.ProductVariantRequestTestBuilder;
import org.egov.product.web.models.ProductVariant;
import org.egov.product.web.models.ProductVariantRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductVariantServiceTest {

    @InjectMocks
    private ProductVariantService productVariantService;

    @Mock
    private IdGenService idGenService;

    @BeforeEach
    void setUp() {

    }

    @Test
    @DisplayName("should enrich the formatted id in product variants")
    void shouldEnrichTheFormattedIdInProductVariants() throws Exception {
        ProductVariantRequest request = ProductVariantRequestTestBuilder.builder()
                .withOneProductVariantAndApiOperationNull()
                .build();
        List<String> idList = new ArrayList<>();
        idList.add("some-id");
        when(idGenService.getIdList(any(RequestInfo.class),
                any(String.class),
                eq("product.variant.id"), eq(""), anyInt()))
                .thenReturn(idList);

        List<ProductVariant> productVariants = productVariantService.create(request);

        assertEquals("some-id", productVariants.get(0).getId());
    }

}