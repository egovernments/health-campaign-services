package digit.web.models.boundaryService;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * BoundaryResponse
 */
@Validated
@jakarta.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-10-16T17:02:11.361704+05:30[Asia/Kolkata]")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BoundaryResponse {

    @JsonProperty("ResponseInfo")
    @Valid
    private ResponseInfo responseInfo = null;

    @JsonProperty("Boundary")
    @Valid
    private List<Boundary> boundary = null;


    public BoundaryResponse addBoundaryItem(Boundary boundaryItem) {
        if (this.boundary == null) {
            this.boundary = new ArrayList<>();
        }
        this.boundary.add(boundaryItem);
        return this;
    }

}
