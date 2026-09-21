package digit.web.models.facility;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacilityDetail {
    @JsonProperty("facility")
    private Facility facility;

    @JsonProperty("additionalInfo")
    private String additionalInfo;
}
