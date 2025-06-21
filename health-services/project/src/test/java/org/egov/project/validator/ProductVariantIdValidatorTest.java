package org.egov.project.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.mdms.MdmsCriteriaReq;
import digit.models.coremodels.mdms.MdmsResponse;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.product.ProductVariant;
import org.egov.common.models.product.ProductVariantResponse;
import org.egov.common.models.product.ProductVariantSearchRequest;
import org.egov.common.models.project.ProjectResource;
import org.egov.common.models.project.ProjectResourceBulkRequest;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.helper.ProjectResourceBulkRequestTestBuilder;
import org.egov.project.validator.resource.PrProductVariantIdValidator;
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

    private ObjectMapper map = new ObjectMapper();

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
                any(MdmsCriteriaReq.class),
                eq(MdmsResponse.class))).thenReturn(emptyResponse());

        Map<ProjectResource, List<Error>> errorDetailsMap = productVariantIdValidator.validate(request);

        assertEquals(1, errorDetailsMap.size());
    }

    @Test
    @DisplayName("should not add to error details if product variant id is not null")
    void shouldNotAddToErrorDetailsIfProductVariantIdIsNotNUll() throws Exception {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequestTestBuilder.builder()
                .withProjectResource().withRequestInfo().build();
        request.getProjectResource().get(0).getResource().setProductVariantId("some-id");
        when(client.fetchResult(any(StringBuilder.class),
                any(MdmsCriteriaReq.class),
                eq(MdmsResponse.class))).thenReturn(someResponse());

        Map<ProjectResource, List<Error>> errorDetailsMap = productVariantIdValidator.validate(request);

        assertEquals(0, errorDetailsMap.size());
    }

    private MdmsResponse someResponse() {
        String jsonString = "{\n" +
                "    \"ResponseInfo\": null,\n" +
                "    \"MdmsRes\": {\n" +
                "        \"HCM-Product\": {\n" +
                "            \"ProductVariants\": [\n" +
                "                {\n" +
                "                    \"id\": \"PVAR-2024-09-13-000001\",\n" +
                "                    \"sku\": \"SP 250mg\",\n" +
                "                    \"tenantId\": \"mz\",\n" +
                "                    \"isDeleted\": false,\n" +
                "                    \"productId\": \"P-2024-09-12-000001\",\n" +
                "                    \"variation\": \"250mg\",\n" +
                "                    \"minQuantity\": 0.5,\n" +
                "                    \"auditDetails\": {\n" +
                "                        \"createdBy\": \"275083d1-7321-45b8-ac91-8e0d5dc0a584\",\n" +
                "                        \"createdTime\": 1726204995000,\n" +
                "                        \"lastModifiedBy\": \"275083d1-7321-45b8-ac91-8e0d5dc0a584\",\n" +
                "                        \"lastModifiedTime\": 1726204995000\n" +
                "                    },\n" +
                "                    \"additionalFields\": {\n" +
                "                        \"fields\": [\n" +
                "                            {\n" +
                "                                \"key\": \"weight\",\n" +
                "                                \"value\": \"5g\"\n" +
                "                            }\n" +
                "                        ],\n" +
                "                        \"schema\": \"test\",\n" +
                "                        \"version\": 1\n" +
                "                    },\n" +
                "                    \"quantityMultiplicationFactor\": 0.5\n" +
                "                }\n" +
                "            ]\n" +
                "        }\n" +
                "    }\n" +
                "}";

        MdmsResponse mdmsResponse = MdmsResponse.builder().build();
        try {
            return map.readValue(jsonString, MdmsResponse.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mdmsResponse;
    }

    private MdmsResponse emptyResponse() {
        String jsonString = "{\n" +
                "    \"ResponseInfo\": null,\n" +
                "    \"MdmsRes\": {\n" +
                "        \"HCM-Product\": {\n" +
                "            \"ProductVariants\": []\n" +
                "        }\n" +
                "    }\n" +
                "}";

        MdmsResponse mdmsResponse = MdmsResponse.builder().build();
        try {
            return map.readValue(jsonString, MdmsResponse.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mdmsResponse;
    }
}
