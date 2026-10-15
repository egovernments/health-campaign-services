package org.egov.common.models.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovModel;
import org.springframework.validation.annotation.Validated;

/**
* Product
*/
@Validated


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

