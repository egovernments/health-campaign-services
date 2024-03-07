package digit.web.models;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import org.egov.common.contract.models.AuditDetails;
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
    @JsonProperty("id")
    @Valid
    private String id = null;

    @JsonProperty("mappedFrom")
    private String mappedFrom = null;

    @JsonProperty("mappedTo")
    private String mappedTo = null;

}
