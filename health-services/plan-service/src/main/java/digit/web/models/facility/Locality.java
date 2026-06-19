package digit.web.models.facility;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.models.AuditDetails;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Locality {

    @JsonProperty("id")
    private String id;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("code")
    private String code;

    @JsonProperty("geometry")
    private String geometry; // Assuming geometry is a string, adjust based on your actual data

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;

    @JsonProperty("additionalDetails")
    private String additionalDetails;
}
