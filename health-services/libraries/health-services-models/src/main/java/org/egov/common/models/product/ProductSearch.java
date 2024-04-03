package org.egov.common.models.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
* ProductSearch
*/
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductSearch {

    @JsonProperty("id")
    private List<String> id = null;

    @JsonProperty("type")
    private String type = null;

    @JsonProperty("name")
    private List<String> name = null;

    @JsonProperty("manufacturer")
    private List<String> manufacturer = null;
}

