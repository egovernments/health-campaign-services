package org.egov.common.models.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
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
public class ProjectSearchURLParams extends URLParams {
    /**
     * Used in project search API to specify if response should include project elements
     * that are in the preceding hierarchy of matched projects.
     */
    @JsonProperty("includeAncestors")
    private Boolean includeAncestors;

    /**
     * Used in project search API to specify if response should include project elements
     * that are in the following hierarchy of matched projects.
     */
    @JsonProperty("includeDescendants")
    private Boolean includeDescendants;

    /**
     * Used in project search API to limit the search results to only those projects whose creation
     * date is after the specified 'createdFrom' date.
     */
    @JsonProperty("createdFrom")
    private Long createdFrom;

    /**
     * Used in project search API to limit the search results to only those projects whose creation
     * date is before the specified 'createdTo' date.
     */
    @JsonProperty("createdTo")
    private Long createdTo;
}
