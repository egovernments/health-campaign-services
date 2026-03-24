package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

import jakarta.validation.constraints.Size;
import org.egov.common.contract.models.AuditDetails;
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
