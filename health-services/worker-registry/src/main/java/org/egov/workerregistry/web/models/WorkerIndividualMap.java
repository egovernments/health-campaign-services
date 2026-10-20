package org.egov.workerregistry.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovModel;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkerIndividualMap extends EgovModel {

    @JsonProperty("workerId")
    private String workerId;

    @JsonProperty("individualId")
    private String individualId;

    @JsonProperty("isDeleted")
    @Builder.Default
    private Boolean isDeleted = false;
}