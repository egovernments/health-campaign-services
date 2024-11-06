package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
 * AdditionalField
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdditionalField {

    @JsonProperty("id")
    @Valid
    @Size(min = 2, max = 64)
    private String id = null;

    @JsonProperty("key")
    @Valid
    @NotNull
    private String key = null;

    @JsonProperty("value")
    @Valid
    @NotNull
    private String value = null;

    @JsonProperty("showOnUi")
    private Boolean showOnUi = Boolean.TRUE;

    @JsonProperty("editable")
    private Boolean editable = Boolean.TRUE;

    @JsonProperty("order")
    private Integer order = null;
}
