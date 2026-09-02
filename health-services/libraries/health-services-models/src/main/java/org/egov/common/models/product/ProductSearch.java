package org.egov.common.models.product;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovSearchModel;
import org.springframework.validation.annotation.Validated;

/**
* ProductSearch
*/
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductSearch extends EgovSearchModel {

    @JsonProperty("type")
    private String type = null;

    @JsonProperty("name")
    private List<String> name = null;

    @JsonProperty("manufacturer")
    private List<String> manufacturer = null;
}

