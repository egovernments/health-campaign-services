package digit.web.models.facility;

import com.fasterxml.jackson.annotation.JsonProperty;
import digit.web.models.Pagination;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacilitySearchCriteria {
    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("id")
    private List<String> id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("isDeleted")
    private Boolean isDeleted;

    @JsonProperty("pagination")
    private Pagination pagination;
}
