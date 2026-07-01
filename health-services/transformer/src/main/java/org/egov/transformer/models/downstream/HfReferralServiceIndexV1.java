package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class HfReferralServiceIndexV1 extends ProjectInfo {
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
    @JsonProperty("boundaryHierarchyCode")
    private Map<String, String> boundaryHierarchyCode;
    @JsonProperty("testedForMalaria")
    private Object testedForMalaria;
    @JsonProperty("malariaResult")
    private Object malariaResult;
    @JsonProperty("admittedWithSeriousIllness")
    private Object admittedWithSeriousIllness;
    @JsonProperty("negativeAndAdmittedWithSeriousIllness")
    private Object negativeAndAdmittedWithSeriousIllness;
    @JsonProperty("treatedWithAntiMalarials")
    private Object treatedWithAntiMalarials;
    @JsonProperty("nameOfAntiMalarials")
    private Object nameOfAntiMalarials;
    @JsonProperty("additionalDetails")
    private ObjectNode additionalDetails;

}
