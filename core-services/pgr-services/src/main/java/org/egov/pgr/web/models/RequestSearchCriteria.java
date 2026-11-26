package org.egov.pgr.web.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.egov.tracer.annotations.CustomSafeHtml;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestSearchCriteria {

    @CustomSafeHtml
    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("tenantIds")
    private Set<String> tenantIds;

    @JsonProperty("serviceCode")
    private Set<String> serviceCode;

    @CustomSafeHtml
    @JsonProperty("hierarchyType")
    private String hierarchyType;

    @JsonProperty("applicationStatus")
    private Set<String> applicationStatus;

    @CustomSafeHtml
    @JsonProperty("mobileNumber")
    private String mobileNumber;

    @CustomSafeHtml
    @JsonProperty("serviceRequestId")
    private String serviceRequestId;

    @JsonProperty("sortBy")
    private SortBy sortBy;

    @JsonProperty("sortOrder")
    private SortOrder sortOrder;
  
    @JsonProperty("locality")
    private Set<String> locality;

    @JsonProperty("ids")
    private Set<String> ids;

    @JsonProperty("fromDate")
    private Long fromDate;

    @JsonProperty("toDate")
    private Long toDate;

    @JsonProperty("slaDeltaMaxLimit")
    private Long slaDeltaMaxLimit;

    @JsonProperty("slaDeltaMinLimit")
    private Long slaDeltaMinLimit;

    @JsonProperty("limit")
    private Integer limit;

    @JsonProperty("offset")
    private Integer offset;

    @JsonIgnore
    private Set<String> userIds;

    @JsonIgnore
    private Boolean isPlainSearch;

    /**
     * Uuid of the user whose complaints (as complainant or creator) the search is restricted to.
     * Set by the server only: it has no setter, so it cannot be bound from the request params.
     */
    @JsonIgnore
    @Setter(AccessLevel.NONE)
    private String restrictedToUserId;

    public void restrictToUser(String userUuid){
        this.restrictedToUserId = userUuid;
    }


    public enum SortOrder {
        ASC,
        DESC
    }

    public enum SortBy {
        locality,
        applicationStatus,
        serviceRequestId
    }

    @CustomSafeHtml
    @JsonProperty("accountId")
    private String accountId;

    public boolean isEmpty(){
        return (this.tenantId==null && this.serviceCode==null && this.mobileNumber==null && this.serviceRequestId==null
        && this.applicationStatus==null && this.ids==null && this.userIds==null && this.locality==null);
    }

}
