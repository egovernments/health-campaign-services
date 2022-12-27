package org.egov.product.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.producer.Producer;
import org.egov.common.service.IdGenService;
import org.egov.product.helper.ProductRequestTestBuilder;
import org.egov.product.repository.ProductRepository;
import org.egov.product.web.models.Product;
import org.egov.product.web.models.ProductRequest;
import org.egov.tracer.model.CustomException;
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
class ProductServiceTest {

    @InjectMocks
    private ProductService productService;

    @Mock
    private IdGenService idGenService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private Producer producer;

    private ProductRequest request;

    @BeforeEach
    void setUp() throws Exception {
        request = ProductRequestTestBuilder.builder()
                .addGoodProduct().withRequestInfo()
                .build();

        List<String> idList = new ArrayList<>();
        idList.add("some-id");
        lenient().when(idGenService.getIdList(any(RequestInfo.class),
                        any(String.class),
                        eq("product.id"), eq(""), anyInt()))
                .thenReturn(idList);
    }

    @Test
    @DisplayName("should enrich the formatted id in products")
    void shouldEnrichTheFormattedIdInProduct() throws Exception {
        ProductRequest response = ProductRequestTestBuilder.builder().addGoodProduct().build();
        response.getProduct().get(0).setId("some-id");

        List<Product> products = productService.create(request);

        assertEquals("some-id", products.get(0).getId());
    }

    @Test
    @DisplayName("should send the enriched product to the kafka topic")
    void shouldSendTheEnrichedProductToTheKafkaTopic() throws Exception {
        ProductRequest response = ProductRequestTestBuilder.builder().addGoodProduct().build();
        response.getProduct().get(0).setId("some-id");

        productService.create(request);

        verify(productRepository, times(1)).save(any(List.class), any(String.class));
    }
    @Test
    void shouldGenerateRequestWithRowVersionAndIsDeleted() throws Exception {
        ProductRequest productRequest = ProductRequestTestBuilder.builder().withRequestInfo().addGoodProduct().build();

        List<Product> products = productService.create(productRequest);
        assertEquals(products.get(0).getRowVersion(), 1);
    }

    @Test
    void shouldGenerateRequestWithAuditDetails() throws Exception {
        ProductRequest productRequest = ProductRequestTestBuilder.builder().withRequestInfo().addGoodProduct().build();

        List<Product> products = productService.create(productRequest);

        assertNotNull(products.get(0).getAuditDetails().getCreatedBy());
        assertNotNull(products.get(0).getAuditDetails().getCreatedTime());
        assertNotNull(products.get(0).getAuditDetails().getLastModifiedBy());
        assertNotNull(products.get(0).getAuditDetails().getLastModifiedTime());
    }

    @Test
    void shouldGenerateRequestWithId() throws Exception {
        ProductRequest productRequest = ProductRequestTestBuilder.builder().withRequestInfo().addGoodProduct().build();
        List<Product> products = productService.create(productRequest);
        assertEquals("some-id", products.get(0).getId());
    }

    @Test
    void shouldThrowErrorWhenIdGenFails() throws Exception{
        when(idGenService.getIdList(any(RequestInfo.class),
                any(String.class),
                eq("product.id"), eq(""), anyInt()))
                .thenThrow(new CustomException("IDGEN_FAILURE", "IDgen service failure"));
        ProductRequest productRequest = ProductRequestTestBuilder.builder().withRequestInfo().addGoodProduct().build();
        assertThrows(Exception.class, () -> productService.create(productRequest));
    }
}