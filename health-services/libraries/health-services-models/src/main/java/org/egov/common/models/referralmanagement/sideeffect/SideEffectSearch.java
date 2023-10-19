package org.egov.common.models.referralmanagement.sideeffect;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SideEffectSearch {
    @JsonProperty("id")
    private List<String> id;

    @JsonProperty("clientReferenceId")
    private List<String> clientReferenceId;

    @JsonProperty("taskId")
    private String taskId;

    @JsonProperty("taskClientReferenceId")
    private String taskClientReferenceId;

}
