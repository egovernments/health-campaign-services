package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.*;
import org.egov.common.models.stock.StockReconciliation;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockReconciliationIndexV1 {


    @JsonProperty("stockReconciliation")
    private StockReconciliation stockReconciliation;

    @JsonProperty("boundaryHierarchy")
    private ObjectNode boundaryHierarchy;

    @JsonProperty("facilityName")
    private String facilityName;

    @JsonProperty("facilityTarget")
    private Long facilityTarget;

    @JsonProperty("facilityLevel")
    private String facilityLevel;

    @JsonProperty("productVariant")
    private String productVariant;

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


}
