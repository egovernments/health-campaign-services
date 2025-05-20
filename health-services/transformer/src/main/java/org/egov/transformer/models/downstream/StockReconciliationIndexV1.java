package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.*;
import org.egov.common.models.stock.StockReconciliation;

import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockReconciliationIndexV1 extends ProjectInfo {


    @JsonProperty("stockReconciliation")
    private StockReconciliation stockReconciliation;

    @JsonProperty("boundaryHierarchy")
    private Map<String, String> boundaryHierarchy;

    @JsonProperty("boundaryHierarchyCode")
    private Map<String, String> boundaryHierarchyCode;

    @JsonProperty("localityCode")
    private String localityCode;

    @JsonProperty("facilityName")
    private String facilityName;

    @JsonProperty("facilityTarget")
    private Long facilityTarget;

    @JsonProperty("facilityLevel")
    private String facilityLevel;

    @JsonProperty("productName")
    private String productName;

    @JsonProperty("additionalDetails")
    private ObjectNode additionalDetails;

    @JsonProperty("syncedTimeStamp")
    private String syncedTimeStamp;

    @JsonProperty("syncedTime")
    private Long syncedTime;

    @JsonProperty("taskDates")
    private String taskDates;

    @JsonProperty("syncedDate")
    private String syncedDate;

    @JsonProperty("userName")
    private String userName;

    @JsonProperty("nameOfUser")
    private String nameOfUser;

    @JsonProperty("role")
    private String role;

    @JsonProperty("userAddress")
    private String userAddress;


}
