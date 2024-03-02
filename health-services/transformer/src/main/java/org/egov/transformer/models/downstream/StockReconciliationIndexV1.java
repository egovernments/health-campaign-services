package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockReconciliationIndexV1 {

    @JsonProperty("id")
    private String id;

    @JsonProperty("boundaryHierarchy")
    private ObjectNode boundaryHierarchy;

    @JsonProperty("clientReferenceId")
    private String clientReferenceId;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("facilityId")
    private String facilityId;

    @JsonProperty("facilityName")
    private String facilityName;

    @JsonProperty("facilityTarget")
    private Long facilityTarget;

    @JsonProperty("facilityLevel")
    private String facilityLevel;

    @JsonProperty("calculatedCount")
    private Integer calculatedCount;

    @JsonProperty("stockPhysicalCount")
    private Integer stockPhysicalCount;

    @JsonProperty("productVariant")
    private String productVariant;

    @JsonProperty("additionalDetails")
    private ObjectNode additionalDetails;

    @JsonProperty("createdBy")
    private String createdBy;

    @JsonProperty("lastModifiedBy")
    private String lastModifiedBy;

    @JsonProperty("createdTime")
    private Long createdTime;

    @JsonProperty("lastModifiedTime")
    private Long lastModifiedTime;

    @JsonProperty("syncedTimeStamp")
    private String syncedTimeStamp;

    @JsonProperty("syncedTime")
    private Long syncedTime;

    @JsonProperty("taskDates")
    private String taskDates;

    @JsonProperty("syncedDate")
    private String syncedDate;


}
