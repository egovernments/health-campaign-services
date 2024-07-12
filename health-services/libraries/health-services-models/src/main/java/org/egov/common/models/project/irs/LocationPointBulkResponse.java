package org.egov.common.models.project.irs;

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
import org.egov.common.models.project.Task;
import org.springframework.validation.annotation.Validated;

/**
* LocationPointResponse
*/
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationPointBulkResponse {

    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private ResponseInfo responseInfo = null;

    @JsonProperty("TotalCount")
    @Valid
    @Builder.Default
    private Long totalCount = 0L;

    @JsonProperty("Tasks")
    @NotNull
    @Valid
    private List<Task> tasks = null;

    public LocationPointBulkResponse addIndividualItem(Task taskItem) {
        if (this.tasks == null) {
            this.tasks = new ArrayList<>();
        }
        this.tasks.add(taskItem);
        return this;
    }

}

