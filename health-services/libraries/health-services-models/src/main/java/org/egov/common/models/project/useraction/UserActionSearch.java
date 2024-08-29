package org.egov.common.models.project.useraction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovOfflineSearchModel;
import org.springframework.validation.annotation.Validated;

/**
 * The UserActionSearch class is used for searching user actions based on various criteria.
 * It extends the EgovOfflineSearchModel to inherit common search properties.
 */
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserActionSearch extends EgovOfflineSearchModel {

    /**
     * A userId to filter the user actions.
     */
    @JsonProperty("createdBy")
    private String createdBy;

    /**
     * A project ID to filter the user actions.
     */
    @JsonProperty("projectId")
    private String projectId;

    /**
     * A beneficiary tag to filter the user actions.
     */
    @JsonProperty("beneficiaryTag")
    private String beneficiaryTag;

    /**
     * A resource tag to filter the user actions.
     */
    @JsonProperty("resourceTag")
    private String resourceTag;

    /**
     * A boundary code to filter the user actions.
     */
    @JsonProperty("boundaryCode")
    private String boundaryCode;
}
