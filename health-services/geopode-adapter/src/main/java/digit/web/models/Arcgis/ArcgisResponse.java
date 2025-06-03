package digit.web.models.Arcgis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArcgisResponse {

    @JsonProperty("objectIdFieldName")
    private String objectIdFieldName;

    @JsonProperty("uniqueIdField")
    @Valid
    private UniqueIdField uniqueIdField;

    @JsonProperty("globalIdFieldName")
    private String globalIdFieldName;

    @JsonProperty("geometryProperties")
    @Valid
    private GeometryProperties geometryProperties;

    @JsonProperty("geometryType")
    private String geometryType;

    @JsonProperty("spatialReference")
    @Valid
    private SpatialReference spatialReference;

    @JsonProperty("fields")
    @Valid
    private List<Field> fields;

    @JsonProperty("features")
    @Valid
    private List<Feature> features;
}
