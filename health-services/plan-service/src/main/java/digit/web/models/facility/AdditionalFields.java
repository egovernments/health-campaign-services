package digit.web.models.facility;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdditionalFields {

    @JsonProperty("schema")
    private String schema;

    @JsonProperty("version")
    private int version;

    @JsonProperty("fields")
    private List<Field> fields;
}
