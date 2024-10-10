package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

/**
 * CensusResponse
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CensusResponse {

    @JsonProperty("ResponseInfo")
    @Valid
    private ResponseInfo responseInfo = null;

    @JsonProperty("Census")
    @Valid
    private List<Census> census = null;


    public CensusResponse addCensusItem(Census censusItem) {
        if (this.census == null) {
            this.census = new ArrayList<>();
        }
        this.census.add(censusItem);
        return this;
    }

}
