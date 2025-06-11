package digit.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

/**
 * GeopodeBoundaryRequest
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public class GeopodeBoundaryRequest {
    @JsonProperty("RequestInfo")
    @Valid
    private RequestInfo requestInfo = null;
    @JsonProperty("BoundarySetup")
    @Valid
    private GeopodeBoundary geopodeBoundary = null;
}
