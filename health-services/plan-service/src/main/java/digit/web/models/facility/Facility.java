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
public class Facility {
    @JsonProperty("id")
    private String id;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("source")
    private String source;

    @JsonProperty("rowVersion")
    private Integer rowVersion;

    @JsonProperty("applicationId")
    private String applicationId;

    @JsonProperty("hasErrors")
    private boolean hasErrors;

    @JsonProperty("additionalFields")
    private String additionalFields;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;

    @JsonProperty("clientReferenceId")
    private String clientReferenceId;

    @JsonProperty("clientAuditDetails")
    private String clientAuditDetails;

    @JsonProperty("isPermanent")
    private boolean isPermanent;

    @JsonProperty("name")
    private String name;

    @JsonProperty("usage")
    private String usage;

    @JsonProperty("storageCapacity")
    private Integer storageCapacity;

    @JsonProperty("address")
    private String address;

    @JsonProperty("isDeleted")
    private boolean isDeleted;
}