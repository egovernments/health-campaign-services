package org.digit.health.delivery.helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.digit.health.delivery.web.models.*;
import org.digit.health.delivery.web.models.request.DeliveryRequest;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;

import java.util.ArrayList;
import java.util.List;


public class DeliveryRequestTestBuilder {
    private DeliveryRequest.DeliveryRequestBuilder builder;
    private ObjectMapper obj = new ObjectMapper();

    public static DeliveryRequestTestBuilder builder() {
        return new DeliveryRequestTestBuilder();
    }

    public DeliveryRequestTestBuilder() {
        this.builder = DeliveryRequest.builder();
    }

    public DeliveryRequestTestBuilder withDeliveryRequest() throws JsonMappingException, JsonProcessingException {
        List<Resource> resourceList = new ArrayList<Resource>();
        resourceList.add(new Resource("GUID", 4, 2, "", true));

        builder.delivery(
                Delivery.builder()
                        .campaignId("ID-1")
                        .registrationId("R-ID-1")
                        .resources(resourceList)
                        .deliveryDate("1663654179")
                        .deliveredBy("UUID")
                        .auditDetails(AuditDetails.builder().build())
                        .status(DeliveryStatus.DELIVERED)
                        .clientReferenceId("GUID")
                        .tenantId("tenantA")
                        .additionalDetails(obj.readTree("{\"schema\":\"DELIVERY\",\"version\":2,\"fields\":[{\"key\":\"height\",\"value\":\"180\"}]}"))
                        .build()
        ).requestInfo(requestInfo());
        return this;
    }
    public RequestInfo requestInfo() {
        return RequestInfo.builder()
                .userInfo(User.builder().uuid("uuid").tenantId("tenantId").id(1L).build()).build();
    }

    public DeliveryRequest build() {
        return builder.build();
    }
}
