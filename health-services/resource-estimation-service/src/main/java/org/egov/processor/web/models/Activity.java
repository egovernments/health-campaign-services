package org.egov.processor.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Activity
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Activity {
    @JsonProperty("id")
    private String id = null;

    @JsonProperty("code")
    @NotNull
    @Size(min = 2, max = 128)
    private String code = null;

    @JsonProperty("description")
    @Size(max = 2048)
    private String description = null;

    @JsonProperty("plannedStartDate")
    private Long plannedStartDate = null;

    @JsonProperty("plannedEndDate")
    private Long plannedEndDate = null;

    @JsonProperty("dependencies")
    private List<String> dependencies = null;

    @JsonProperty("conditions")
    @Valid
    private List<Condition> conditions = null;

}
