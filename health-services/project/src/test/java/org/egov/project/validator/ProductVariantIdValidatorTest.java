package org.egov.project.validator;

import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.project.helper.ProjectResourceBulkRequestTestBuilder;
import org.egov.project.validator.resource.PrNonExistentEntityValidator;
import org.egov.project.web.models.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProductVariantIdValidatorTest {
    
    @Mock
    PrNonExistentEntityValidator prNonExistentEntityValidator;


    @Mock
    private ServiceRequestClient client;

//    @BeforeEach
//    void setUp() {
//        lenient().when(stockConfiguration.getProductHost()).thenReturn("http://localhost:8080/");
//        lenient().when(stockConfiguration.getProductVariantSearchUrl()).thenReturn("/some-url");
//    }

    @Test
    @DisplayName("should add to error details if product variant id is null")
    void shouldAddToErrorDetailsIfProjectVariantIdIsNull() throws Exception {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequestTestBuilder.builder()
                .withProjectResource().build();
        when(client.fetchResult(any(StringBuilder.class),
                any(ProductVariantSearchRequest.class),
                eq(ProductVariantResponse.class))).thenReturn(emptyResponse());

        Map<ProjectResource, List<Error>> errorDetailsMap = prNonExistentEntityValidator.validate(request);
        assertEquals(1, errorDetailsMap.size());
    }

    private ProductVariantResponse someResponse() {
        return ProductVariantResponse.builder().productVariant(Collections
                .singletonList(ProductVariant.builder().id("some-id").build())).build();
    }

    private ProductVariantResponse emptyResponse() {
        return ProductVariantResponse.builder().productVariant(Collections.emptyList()).build();
    }
}
