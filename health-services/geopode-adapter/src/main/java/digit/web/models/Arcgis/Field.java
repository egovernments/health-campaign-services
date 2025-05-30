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
public class Field {

    @JsonProperty("name")
    private String name;

    @JsonProperty("type")
    private String type;

    @JsonProperty("alias")
    private String alias;

    @JsonProperty("sqlType")
    private String sqlType;

    @JsonProperty("length")
    private Integer length;

    @JsonProperty("domain")
    private Object domain;

    @JsonProperty("defaultValue")
    private Object defaultValue;
}
