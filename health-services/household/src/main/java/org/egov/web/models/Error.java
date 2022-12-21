package org.egov.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
* Error object will be returned as a part of reponse body in conjunction with ResponseInfo as part of ErrorResponse whenever the request processing status in the ResponseInfo is FAILED. HTTP return in this scenario will usually be HTTP 400.
*/
    @ApiModel(description = "Error object will be returned as a part of reponse body in conjunction with ResponseInfo as part of ErrorResponse whenever the request processing status in the ResponseInfo is FAILED. HTTP return in this scenario will usually be HTTP 400.")
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-21T13:41:16.379+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Error   {
        @JsonProperty("code")
      @NotNull



    private String code = null;

        @JsonProperty("message")
      @NotNull



    private String message = null;

        @JsonProperty("description")
    


    private String description = null;

        @JsonProperty("params")
    


    private List<String> params = null;


        public Error addParamsItem(String paramsItem) {
            if (this.params == null) {
            this.params = new ArrayList<>();
            }
        this.params.add(paramsItem);
        return this;
        }

}

