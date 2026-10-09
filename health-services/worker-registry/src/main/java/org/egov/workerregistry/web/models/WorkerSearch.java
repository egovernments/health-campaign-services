package org.egov.workerregistry.web.models;

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
public class WorkerSearch {

    @JsonProperty("id")
    private List<String> id;

    @JsonProperty("individualId")
    private List<String> individualId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("tenantId")
    private String tenantId;
}
