package org.egov.common.models.individual;

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
import org.springframework.validation.annotation.Validated;

/**
 * IndividualBulkResponse represents the response structure for bulk operations related to individuals.
 * It encapsulates the response information, total count of individuals, and a list of individual objects.
 */
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndividualBulkResponse {

    /**
     * Metadata about the API response, including details like request status and other information.
     */
    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private ResponseInfo responseInfo = null;

    /**
     * Total count of individual records in the response, defaults to 0 if not specified.
     */
    @JsonProperty("TotalCount")
    @Valid
    @Builder.Default
    private Long totalCount = 0L;

    /**
     * List of individual records returned in the response.
     */
    @JsonProperty("Individual")
    @Valid
    private List<Individual> individual = null;

    /**
     * Adds a single individual record to the list and returns the updated response.
     *
     * @param individualItem The individual record to add to the list.
     * @return The updated IndividualBulkResponse instance.
     */
    public IndividualBulkResponse addIndividualItem(Individual individualItem) {
        if (this.individual == null) {
            this.individual = new ArrayList<>();
        }
        this.individual.add(individualItem);
        return this;
    }
}
