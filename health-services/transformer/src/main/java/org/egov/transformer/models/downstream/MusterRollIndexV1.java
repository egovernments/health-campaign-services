package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.transformer.models.musterRoll.MusterRoll;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MusterRollIndexV1 {
    @JsonProperty("musterRoll")
    private MusterRoll musterRoll;
    @JsonProperty("boundaryHierarchy")
    private Map<String, String> boundaryHierarchy;
    @JsonProperty("boundaryHierarchyCode")
    private Map<String, String> boundaryHierarchyCode;
    @JsonProperty("taskDates")
    private String taskDates;
    @JsonProperty("syncedDate")
    private String syncedDate;
    @JsonProperty("additionalDetails")
    private ObjectNode additionalDetails;

}
