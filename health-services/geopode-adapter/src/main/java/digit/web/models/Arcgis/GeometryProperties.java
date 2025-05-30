package digit.web.models.Arcgis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeometryProperties {

    @JsonProperty("shapeAreaFieldName")
    private String shapeAreaFieldName;

    @JsonProperty("shapeLengthFieldName")
    private String shapeLengthFieldName;

    @JsonProperty("units")
    private String units;
}
