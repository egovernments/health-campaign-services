package digit.web.models;

import digit.models.coremodels.AuditDetails;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.validation.annotation.Validated;
import javax.validation.Valid;
import javax.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

/**
 * ResourceMapping
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResourceMapping {
    @JsonProperty("mappedFrom")
    private String mappedFrom = null;

    @JsonProperty("mappedTo")
    private String mappedTo = null;

    @JsonProperty("auditDetails")
    private @Valid AuditDetails auditDetails;


}
