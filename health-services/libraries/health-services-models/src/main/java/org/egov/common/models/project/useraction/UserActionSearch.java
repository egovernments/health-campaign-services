package org.egov.common.models.project.useraction;

import java.util.List;

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
     * A list of project IDs to filter the user actions.
     */
    @JsonProperty("projectId")
    private List<String> projectId;

    /**
     * A list of beneficiary tags to filter the user actions.
     */
    @JsonProperty("beneficiaryTag")
    private List<String> beneficiaryTag;

    /**
     * A list of resource tags to filter the user actions.
     */
    @JsonProperty("resourceTag")
    private List<String> resourceTag;

    /**
     * A list of boundary codes to filter the user actions.
     */
    @JsonProperty("boundaryCode")
    private List<String> boundaryCode;
}
