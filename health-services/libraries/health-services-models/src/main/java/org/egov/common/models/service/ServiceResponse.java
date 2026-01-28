package org.egov.common.models.service;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;

import org.egov.common.models.core.Pagination;
import org.springframework.validation.annotation.Validated;

/**
 * ServiceResponse
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ServiceResponse {
    @JsonProperty("ResponseInfo")
    @Valid
    private ResponseInfo responseInfo = null;

    @JsonProperty("Services")
    @Valid
    private List<Service> services = null;

    @JsonProperty("Pagination")
    @Valid
    private Pagination pagination = null;

    public ServiceResponse addServiceItem(Service serviceItem) {
        if (this.services == null) {
            this.services = new ArrayList<>();
        }
        this.services.add(serviceItem);
        return this;
    }

}
