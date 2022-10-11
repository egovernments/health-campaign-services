package org.digit.health.delivery.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Delivery extends CampaignData{

    @JsonProperty("deliveryId")
    private String deliveryId;

    @JsonProperty("campaignId")
    private String campaignId;

    @JsonProperty("registrationId")
    private String registrationId;

    @JsonProperty("clientReferenceId")
    private String clientReferenceId;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("status")
    private DeliveryStatus status;

    @JsonProperty("resources")
    private List<Resource> resources;

    @JsonProperty("deliveryDate")
    private String deliveryDate;

    @JsonProperty("deliveredBy")
    private String deliveredBy;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;

    @JsonProperty("additionalDetails")
    private JsonNode additionalDetails;
}
