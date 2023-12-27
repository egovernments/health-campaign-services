package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceIndexV2 {
    @JsonProperty("id")
    private String id;
    @JsonProperty("supervisorLevel")
    private String supervisorLevel;
    @JsonProperty("checklistName")
    private String checklistName;
    @JsonProperty("ageGroup")
    private String ageGroup;
    @JsonProperty("childrenPresented")
    private Object childrenPresented;
    @JsonProperty("feverPositive")
    private Object feverPositive;
    @JsonProperty("feverNegative")
    private Object feverNegative;
    @JsonProperty("referredChildrenToAPE")
    private Object referredChildrenToAPE;
    @JsonProperty("referredChildrenPresentedToAPE")
    private Object referredChildrenPresentedToAPE;
    @JsonProperty("positiveMalaria")
    private Object positiveMalaria;
    @JsonProperty("negativeMalaria")
    private Object negativeMalaria;

}
