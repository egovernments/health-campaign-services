package org.egov.excelingestion.web.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Campaign search response model
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignSearchResponse {
    
    @JsonProperty("ResponseInfo")
    private ResponseInfo ResponseInfo;

    @JsonProperty("CampaignDetails")
    private List<CampaignDetail> CampaignDetails;

    private Long totalCount;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CampaignDetail {
        private String id;
        private String tenantId;
        private String projectType;
        private String hierarchyType;
        private String boundaryCode;
        private String campaignNumber;
        private List<BoundaryDetail> boundaries;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BoundaryDetail {
        private String code;
        private String name;
        private String type;
        private Boolean isRoot;
        private String parent;
        private Boolean includeAllChildren;
    }
}