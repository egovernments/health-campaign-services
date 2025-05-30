package digit.web.models.Arcgis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Feature {

    @JsonProperty("attributes")
    @Valid
    private Attributes attributes; // Contains ADM1_NAME

//    @JsonProperty("geometry")
//    @Valid
//    private Geometry geometry;     // Contains rings
}
