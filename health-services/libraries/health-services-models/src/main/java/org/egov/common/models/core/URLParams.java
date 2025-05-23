package org.egov.common.models.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
/**
 * Model class representing common search criteria for API search operations.
 * @author kanishq-egov
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
    @Max(2000)
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
    @Builder.Default
    @JsonProperty("includeDeleted")
    private Boolean includeDeleted = Boolean.FALSE;

    /**
     * Sets the URL parameters from the given URLParams object.
     * This method allows updating the current URLParams instance with values from another instance.
     *
     * @param urlParams the URL parameters to set
     */
    public void setURLParams(URLParams urlParams) {
        // Update limit if provided in the input URLParams
        if (urlParams.getLimit() != null) this.limit = urlParams.getLimit();

        // Update offset if provided in the input URLParams
        if (urlParams.getOffset() != null) this.offset = urlParams.getOffset();

        // Update tenantId if provided in the input URLParams
        if (urlParams.getTenantId() != null) this.tenantId = urlParams.getTenantId();

        // Update lastChangedSince if provided in the input URLParams
        if (urlParams.getLastChangedSince() != null) this.lastChangedSince = urlParams.getLastChangedSince();

        // Update includeDeleted if provided in the input URLParams
        if (urlParams.getIncludeDeleted() != null) this.includeDeleted = urlParams.getIncludeDeleted();
    }

}
