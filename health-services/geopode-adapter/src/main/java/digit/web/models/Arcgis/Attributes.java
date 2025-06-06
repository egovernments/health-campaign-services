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
public class Attributes {

    @JsonProperty("ADM0_NAME")
    private String ADM0_NAME;

    @JsonProperty("ADM1_NAME")
    private String ADM1_NAME;

    @JsonProperty("ADM2_NAME")
    private String ADM2_NAME;

    @JsonProperty("ADM3_NAME")
    private String ADM3_NAME;

    @JsonProperty("ADM4_NAME")
    private String ADM4_NAME;
}
