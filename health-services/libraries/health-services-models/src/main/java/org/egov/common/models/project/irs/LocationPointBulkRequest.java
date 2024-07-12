package org.egov.common.models.project.irs;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.project.Task;
import org.springframework.validation.annotation.Validated;

/**
* TaskRequest
*/
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationPointBulkRequest {

    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private RequestInfo requestInfo = null;

    @JsonProperty("Tasks")
    @NotNull
    @Valid
    @Size(min=1)
    private List<Task> tasks = new ArrayList<>();

    public LocationPointBulkRequest addTaskItem(Task taskItem) {
        this.tasks.add(taskItem);
        return this;
    }
}

