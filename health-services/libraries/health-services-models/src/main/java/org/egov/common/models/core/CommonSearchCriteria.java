package org.egov.common.models.core;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiParam;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model class representing common search criteria for API search operations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommonSearchCriteria {

    /**
     * The maximum number of records to be returned in the response.
     */
    @NotNull
    @Min(0)
    @Max(1000)
    @JsonProperty("limit")
    @ApiParam(value = "Pagination - limit records in response", required = true)
    private Integer limit;

    /**
     * The offset from which records should be returned in the response.
     */
    @NotNull
    @Min(0)
    @JsonProperty("offset")
    @ApiParam(value = "Pagination - offset from which records should be returned in response", required = true)
    private Integer offset;

    /**
     * The unique identifier for the tenant.
     */
    @NotNull
    @JsonProperty("tenantId")
    @ApiParam(value = "Unique id for a tenant.", required = true)
    private String tenantId;

    /**
     * The epoch time representing the starting point from which changes on the object should be picked up.
     * Results from this parameter should include both newly created objects and modified objects since this time.
     * This criterion aids polling clients to synchronize changes since their last synchronization with the platform.
     */
    @JsonProperty("lastChangedSince")
    @ApiParam(value = "Epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ")
    private Long lastChangedSince;

    /**
     * Flag indicating whether soft deleted records should be included in search results.
     * This flag is used in search APIs to specify if deleted records should be included.
     */
    @JsonProperty("includeDeleted")
    @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false")
    private Boolean includeDeleted;

}
