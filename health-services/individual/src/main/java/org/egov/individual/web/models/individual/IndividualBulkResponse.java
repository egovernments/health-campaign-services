package org.egov.individual.web.models.individual;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.models.individual.Individual;
import org.springframework.validation.annotation.Validated;

/**
 * IndividualResponse
 */
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndividualBulkResponse {

    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private ResponseInfo responseInfo = null;

    @JsonProperty("TotalCount")
    @Valid
    @Builder.Default
    private Long totalCount = 0L;

    @JsonProperty("Individual")
    @Valid
    private List<Individual> individual = null;

    public IndividualBulkResponse addIndividualItem(Individual individualItem) {
        if (this.individual == null) {
            this.individual = new ArrayList<>();
        }
        this.individual.add(individualItem);
        return this;
    }

}
