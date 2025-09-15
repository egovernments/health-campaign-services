package org.egov.excelingestion.web.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Campaign search request model
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignSearchRequest {
    
    private RequestInfo RequestInfo;
    private CampaignSearchCriteria CampaignDetails;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CampaignSearchCriteria {
        private String tenantId;
        private Boolean isActive;
        private String[] ids;
        private Pagination pagination;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Pagination {
        private Integer limit;
        private Integer offset;
    }
}