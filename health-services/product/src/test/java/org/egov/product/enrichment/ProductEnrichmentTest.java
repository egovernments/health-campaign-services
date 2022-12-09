package org.egov.product.enrichment;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.producer.Producer;
import org.egov.common.service.IdGenService;
import org.egov.product.helper.ProductRequestTestBuilder;
import org.egov.product.web.models.ProductRequest;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProductEnrichmentTest {

    @InjectMocks
    ProductEnrichment productEnrichment;

    @Mock
    IdGenService idGenService;

    @BeforeEach
    void setUp() throws Exception {
        List<String> idList = new ArrayList<>();
        idList.add("some-id");
        lenient().when(idGenService.getIdList(any(RequestInfo.class),
                any(String.class),
                eq("product.id"), eq(""), anyInt()))
                .thenReturn(idList);
    }

    @Test
    public void shouldGenerateRequestWithRowVersionAndIsDeleted() throws Exception {
        ProductRequest productRequest = ProductRequestTestBuilder.builder().withRequestInfo().addGoodProduct().build();

        ProductRequest productRequest1 = productEnrichment.enrichProduct(productRequest);
        assertEquals(productRequest1.getProduct().get(0).getRowVersion(), 1);
    }

    @Test
    public void shouldGenerateRequestWithAuditDetails() throws Exception {
        ProductRequest productRequest = ProductRequestTestBuilder.builder().withRequestInfo().addGoodProduct().build();

        productRequest = productEnrichment.enrichProduct(productRequest);

        assertNotNull(productRequest.getProduct().stream().findAny().get().getAuditDetails().getCreatedBy());
        assertNotNull(productRequest.getProduct().stream().findAny().get().getAuditDetails().getCreatedTime());
        assertNotNull(productRequest.getProduct().stream().findAny().get().getAuditDetails().getLastModifiedBy());
        assertNotNull(productRequest.getProduct().stream().findAny().get().getAuditDetails().getLastModifiedTime());
    }

    @Test
    public void shouldGenerateRequestWithId() throws Exception {
        ProductRequest productRequest = ProductRequestTestBuilder.builder().withRequestInfo().addGoodProduct().build();
        productRequest = productEnrichment.enrichProduct(productRequest);
        assertEquals("some-id", productRequest.getProduct().get(0).getId());
    }

    @Test
    public void shouldThrowErrorWhenIdGenFails() throws Exception{
        when(idGenService.getIdList(any(RequestInfo.class),
                any(String.class),
                eq("product.id"), eq(""), anyInt()))
                .thenThrow(new CustomException("IDGEN_FAILURE", "IDgen service failure"));
        ProductRequest productRequest = ProductRequestTestBuilder.builder().withRequestInfo().addGoodProduct().build();
        assertThrows(Exception.class, () -> productEnrichment.enrichProduct(productRequest));
    }
}
