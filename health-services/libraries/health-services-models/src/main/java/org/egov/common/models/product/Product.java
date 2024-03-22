package org.egov.common.models.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovModel;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
* Product
*/
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T16:45:24.641+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Product extends EgovModel {


    @JsonProperty("type")
    @NotNull
    @Size(min = 2, max = 100)
    private String type = null;

    @JsonProperty("name")
    @NotNull
    @Size(min = 2, max = 1000)
    private String name = null;

    @JsonProperty("manufacturer")
    @Size(min = 0, max = 1000)
    private String manufacturer = null;

    //TODO remove
    @JsonProperty("isDeleted")
    private Boolean isDeleted = null;

}

