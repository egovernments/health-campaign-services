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
public class HfFeverServiceIndexV1 {
    @JsonProperty("id")
    private String id;
    @JsonProperty("supervisorLevel")
    private String supervisorLevel;
    @JsonProperty("checklistName")
    private String checklistName;
    @JsonProperty("createdTime")
    private Long createdTime;
    @JsonProperty("createdBy")
    private String createdBy;
    @JsonProperty("userName")
    private String userName;
    @JsonProperty("role")
    private String role;
    @JsonProperty("userAddress")
    private String userAddress;
    @JsonProperty("projectId")
    private String projectId;
    @JsonProperty("tenantId")
    private String tenantId;
    @JsonProperty("syncedTime")
    private Long syncedTime;
    @JsonProperty("taskDates")
    private String taskDates;
    @JsonProperty("syncedTimeStamp")
    private String syncedTimeStamp;
    @JsonProperty("boundaryHierarchy")
    private ObjectNode boundaryHierarchy;
    @JsonProperty("testedForMalaria")
    private Object testedForMalaria;
    @JsonProperty("malariaResult")
    private Object malariaResult;
    @JsonProperty("admittedWithSeriousIllness")
    private Object admittedWithSeriousIllness;
    @JsonProperty("treatedWithAntiMalarials")
    private Object treatedWithAntiMalarials;
    @JsonProperty("negativeRTDGivenSPAQ")
    private Object negativeRTDGivenSPAQ;
    @JsonProperty("negativeRTDGivenSPAQOutcome")
    private Object negativeRTDGivenSPAQOutcome;
    @JsonProperty("nameOfAntiMalarials")
    private Object nameOfAntiMalarials;
    @JsonProperty("additionalDetails")
    private ObjectNode additionalDetails;

}
