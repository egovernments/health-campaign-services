package org.egov.servicerequest.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

/**
 * ServiceResponse
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ServiceResponse {
    @JsonProperty("responseInfo")
    @Valid
    private ResponseInfo responseInfo = null;

    @JsonProperty("service")
    @Valid
    private List<Service> service = null;

    @JsonProperty("pagination")
    @Valid
    private Pagination pagination = null;


    public ServiceResponse addServiceItem(Service serviceItem) {
        if (this.service == null) {
            this.service = new ArrayList<>();
        }
        this.service.add(serviceItem);
        return this;
    }

}
