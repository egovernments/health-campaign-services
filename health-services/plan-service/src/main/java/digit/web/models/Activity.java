package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Set;

import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

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
    private String code = null;

    @JsonProperty("description")
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
