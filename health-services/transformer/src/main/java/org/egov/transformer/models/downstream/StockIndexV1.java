package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.models.stock.AdditionalFields;
import org.egov.common.models.stock.TransactionReason;
import org.egov.common.models.stock.TransactionType;

import java.util.Map;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockIndexV1 {

    @JsonProperty("id")
    private String id;

    @JsonProperty("facilityId")
    private String facilityId;

    @JsonProperty("transactingFacilityId")
    private String transactingFacilityId;

    @JsonProperty("facilityName")
    private String facilityName;

    @JsonProperty("transactingFacilityName")
    private String transactingFacilityName;

    @JsonProperty("productVariant")
    private String productVariant;

    @JsonProperty("productName")
    private String productName;

    @JsonProperty("physicalCount")
    private Integer physicalCount;

    @JsonProperty("eventType")
    private TransactionType eventType;

    @JsonProperty("reason")
    private TransactionReason reason;

    @JsonProperty("userName")
    private String userName;

    @JsonProperty("nameOfUser")
    private String nameOfUser;

    @JsonProperty("role")
    private String role;

    @JsonProperty("userAddress")
    private String userAddress;

    @JsonProperty("dateOfEntry")
    private Long dateOfEntry;

    @JsonProperty("boundaryHierarchy")
    private Map<String, String> boundaryHierarchy;

    @JsonProperty("boundaryHierarchyCode")
    private Map<String, String> boundaryHierarchyCode;

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

    @JsonProperty("additionalFields")
    private AdditionalFields additionalFields;

    @JsonProperty("clientReferenceId")
    private String clientReferenceId;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("facilityType")
    private String facilityType;

    @JsonProperty("transactingFacilityType")
    private String transactingFacilityType;

    @JsonProperty("facilityLevel")
    private String facilityLevel;

    @JsonProperty("transactingFacilityLevel")
    private String transactingFacilityLevel;

    @JsonProperty("facilityTarget")
    private Long facilityTarget;

    @JsonProperty("taskDates")
    private String taskDates;

    @JsonProperty("syncedDate")
    private String syncedDate;

    @JsonProperty("additionalDetails")
    private ObjectNode additionalDetails;

    @JsonProperty("waybillNumber")
    private String waybillNumber;

}
