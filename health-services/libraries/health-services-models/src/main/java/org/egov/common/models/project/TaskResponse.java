package org.egov.common.models.project;

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
* TaskResponse
*/
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskResponse   {

    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private ResponseInfo responseInfo = null;

    @JsonProperty("Task")
    @NotNull
    @Valid
    private Task task = null;

}

