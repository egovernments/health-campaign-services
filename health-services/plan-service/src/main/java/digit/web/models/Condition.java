package digit.web.models;

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
 * Condition
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Condition {

    @JsonProperty
    private String id = null;

    @JsonProperty("entity")
    private String entity = null;

    @JsonProperty("entityProperty")
    private String entityProperty = null;

    @JsonProperty("expression")
    private String expression = null;

}
