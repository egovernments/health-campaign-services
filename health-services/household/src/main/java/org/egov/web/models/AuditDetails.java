package org.egov.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
* Collection of audit related fields used by most models
*/
    @ApiModel(description = "Collection of audit related fields used by most models")
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-21T13:41:16.379+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditDetails   {
        @JsonProperty("createdBy")
    


    private String createdBy = null;

        @JsonProperty("lastModifiedBy")
    


    private String lastModifiedBy = null;

        @JsonProperty("createdTime")
    


    private Long createdTime = null;

        @JsonProperty("lastModifiedTime")
    


    private Long lastModifiedTime = null;


}

