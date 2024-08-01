package org.egov.common.models.project.useraction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovOfflineModel;
import org.egov.common.models.project.TaskAction;
import org.springframework.validation.annotation.Validated;

/**
 * The UserAction class represents a user action related to a project.
 * It extends the EgovOfflineModel to inherit common properties.
 */
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserAction extends EgovOfflineModel {

    /**
     * The ID of the project associated with the user action.
     * It must be between 2 and 64 characters long and cannot be null.
     */
    @JsonProperty("projectId")
    @Size(min = 2, max = 64)
    @NotNull
    private String projectId;

    /**
     * The latitude coordinate of the user action's location.
     * It must be between -90 and 90 degrees and cannot be null.
     */
    @JsonProperty("latitude")
    @DecimalMin("-90")
    @DecimalMax("90")
    @NotNull
    private Double latitude;

    /**
     * The longitude coordinate of the user action's location.
     * It must be between -180 and 180 degrees and cannot be null.
     */
    @JsonProperty("longitude")
    @DecimalMin("-180")
    @DecimalMax("180")
    @NotNull
    private Double longitude;

    /**
     * The accuracy of the location measurement in meters.
     * It must be a positive number and cannot be null.
     */
    @JsonProperty("locationAccuracy")
    @DecimalMin("0")
    @NotNull
    private Double locationAccuracy;

    /**
     * The code of the boundary where the user action took place.
     * It cannot be null.
     */
    @JsonProperty("boundaryCode")
    @NotNull
    private String boundaryCode;

    /**
     * The action performed by the user, represented as a TaskAction object.
     * It cannot be null.
     */
    @JsonProperty("action")
    @NotNull
    private TaskAction action;

    /**
     * An optional tag for the beneficiary associated with the user action.
     * It must be between 2 and 64 characters long.
     */
    @JsonProperty("beneficiaryTag")
    @Size(min = 2, max = 64)
    private String beneficiaryTag;

    /**
     * An optional tag for the resource associated with the user action.
     * It must be between 2 and 64 characters long.
     */
    @JsonProperty("resourceTag")
    @Size(min = 2, max = 64)
    private String resourceTag;

    /**
     * A flag indicating whether the user action has been deleted.
     * The default value is false.
     */
    @JsonProperty("isDeleted")
    @Builder.Default
    private Boolean isDeleted = false;

}
