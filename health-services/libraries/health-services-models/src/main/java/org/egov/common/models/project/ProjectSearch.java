package org.egov.common.models.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovSearchModel;
import org.egov.common.models.core.ProjectSearchURLParams;
import org.springframework.validation.annotation.Validated;

/**
 * ProjectSearch - Model class for searching projects.
 * This class includes various fields that can be used to filter projects based on specific criteria.
 * It extends EgovSearchModel to inherit common search-related properties.
 */
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectSearch extends EgovSearchModel {

    /**
     * The start date of the project.
     * This field can be used to filter projects that start from a specific date.
     */
    @JsonProperty("startDate")
    private Long startDate;

    /**
     * The end date of the project.
     * This field can be used to filter projects that end by a specific date.
     */
    @JsonProperty("endDate")
    private Long endDate;

    /**
     * Indicates if the project has tasks enabled.
     * Default value is FALSE.
     */
    @JsonProperty("isTaskEnabled")
    private Boolean isTaskEnabled = Boolean.FALSE;

    /**
     * The parent project ID.
     * This field can be used to filter sub-projects of a specific parent project.
     * It should be between 2 and 64 characters in length.
     */
    @JsonProperty("parent")
    @Size(min = 2, max = 64)
    private String parent;

    /**
     * The ID of the project type.
     * This field can be used to filter projects of a specific type.
     */
    @JsonProperty("projectTypeId")
    private String projectTypeId;

    /**
     * The ID of the sub-project type.
     * This field can be used to filter projects of a specific sub-type.
     */
    @JsonProperty("subProjectTypeId")
    private String subProjectTypeId;

    /**
     * The department associated with the project.
     * This field can be used to filter projects of a specific department.
     * It should be between 2 and 64 characters in length.
     */
    @JsonProperty("department")
    @Size(min = 2, max = 64)
    private String department;

    /**
     * The reference ID of the project.
     * This field can be used to filter projects by their reference ID.
     * It should be between 2 and 100 characters in length.
     */
    @JsonProperty("referenceId")
    @Size(min = 2, max = 100)
    private String referenceId;

    /**
     * The boundary code associated with the project.
     * This field can be used to filter projects within a specific boundary.
     */
    @JsonProperty("boundaryCode")
    private String boundaryCode;

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

    /**
     * The name of the project.
     * This field can be used to filter projects by their name.
     */
    @JsonProperty("name")
    private String name;

    /**
     * Sets the URL parameters from the given ProjectSearchURLParams object to this ProjectSearch object.
     * This method allows for easy transfer of search parameters from URL to the search model.
     *
     * @param urlParams The ProjectSearchURLParams object containing the URL parameters.
     */
    public void setURLParams(ProjectSearchURLParams urlParams) {
        // Call the superclass method to set common URL parameters

        // If the URL parameter includeAncestors is not null, set it to the current object's includeAncestors field
        if (urlParams.getIncludeAncestors() != null) {
            includeAncestors = urlParams.getIncludeAncestors();
        }

        // If the URL parameter includeDescendants is not null, set it to the current object's includeDescendants field
        if (urlParams.getIncludeDescendants() != null) {
            includeDescendants = urlParams.getIncludeDescendants();
        }

        // If the URL parameter createdFrom is not null, set it to the current object's createdFrom field
        if (urlParams.getCreatedFrom() != null) {
            createdFrom = urlParams.getCreatedFrom();
        }

        // If the URL parameter createdTo is not null, set it to the current object's createdTo field
        if (urlParams.getCreatedTo() != null) {
            createdTo = urlParams.getCreatedTo();
        }
    }
}