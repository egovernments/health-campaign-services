package org.egov.transformer.models.upstream;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * ServiceDefinitionResponse
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ServiceDefinitionResponse {
    @JsonProperty("ResponseInfo")
    private ResponseInfo responseInfo = null;

    @JsonProperty("ServiceDefinitions")
    private List<ServiceDefinition> serviceDefinition = null;

    @JsonProperty("Pagination")
    private Pagination pagination = null;


    public ServiceDefinitionResponse addServiceDefinitionItem(ServiceDefinition serviceDefinitionItem) {
        if (this.serviceDefinition == null) {
            this.serviceDefinition = new ArrayList<>();
        }
        this.serviceDefinition.add(serviceDefinitionItem);
        return this;
    }

}
