package org.egov.project.validator;

import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.helper.ProjectResourceBulkRequestTestBuilder;
import org.egov.project.validator.resource.PrProductVariantIdValidator;
import org.egov.project.web.models.ProductVariant;
import org.egov.project.web.models.ProductVariantResponse;
import org.egov.project.web.models.ProductVariantSearchRequest;
import org.egov.project.web.models.ProjectResource;
import org.egov.project.web.models.ProjectResourceBulkRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductVariantIdValidatorTest {
    
    @InjectMocks
    PrProductVariantIdValidator productVariantIdValidator;

    @Mock
    ProjectConfiguration projectConfiguration;

    @Mock
    private ServiceRequestClient client;

    @BeforeEach
    void setUp() {
        lenient().when(projectConfiguration.getProductHost()).thenReturn("http://localhost:8080/");
        lenient().when(projectConfiguration.getProductVariantSearchUrl()).thenReturn("/some-url");
    }

    @Test
    @DisplayName("should add to error details if product variant id is null")
    void shouldAddToErrorDetailsIfProjectVariantIdIsNull() throws Exception {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequestTestBuilder.builder()
                .withProjectResource().build();
        when(client.fetchResult(any(StringBuilder.class),
                any(ProductVariantSearchRequest.class),
                eq(ProductVariantResponse.class))).thenReturn(emptyResponse());

        Map<ProjectResource, List<Error>> errorDetailsMap = productVariantIdValidator.validate(request);

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
