package org.egov.pgr.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Response to the service request
 */
@ApiModel(description = "Response to the service request")
@Validated
@jakarta.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2020-07-15T11:35:33.568+05:30")

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ServiceResponse   {
        @JsonProperty("responseInfo")
        private ResponseInfo responseInfo = null;

        @JsonProperty("ServiceWrappers")
        private List<ServiceWrapper> serviceWrappers = null;
        
        @JsonProperty("complaintsResolved")
        private int complaintsResolved;

        @JsonProperty("averageResolutionTime")
        private int averageResolutionTime;

        @JsonProperty("complaintTypes")
        private int complaintTypes;


}

