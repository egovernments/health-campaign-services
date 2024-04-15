package org.egov.common.models.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
//TODO should we move all this to body model or should we keep this in url? same with search common models
/**
 * Model class representing common search criteria for API search operations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class URLParams {

    /**
     * The maximum number of records to be returned in the response.
     */
    @NotNull
    @Min(0)
    @Max(1000)
    @JsonProperty("limit")
    private Integer limit;

    /**
     * The offset from which records should be returned in the response.
     */
    @NotNull
    @Min(0)
    @JsonProperty("offset")
    private Integer offset;

    /**
     * The unique identifier for the tenant.
     */
    @NotNull
    @JsonProperty("tenantId")
    private String tenantId;

    /**
     * The epoch time representing point in time since last modification happened in the table.
     * Results from this parameter should include both newly created objects and modified objects from this time.
     * This criterion aids polling clients to synchronize changes since their last synchronization with the platform.
     */
    @JsonProperty("lastChangedSince")
    private Long lastChangedSince;

    /**
     * Flag indicating whether soft deleted records should be included in search results.
     * This flag is used in search APIs to specify if deleted records should be included.
     */
    @JsonProperty("includeDeleted")
    private Boolean includeDeleted;

}
