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
public class Field {

    @JsonProperty("key")
    private String key;

    @JsonProperty("value")
    private String value;
}