package org.egov.common.models.project;


import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;


/**
 * @author syed-egov
 * POJO to capture the metadata of Task
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskQuantity {

    @JsonProperty("id")
    private List<String> id = null;

    @JsonProperty("regex")
    private String regex = null;

    @JsonProperty("errorMessage")
    private String errorMessage = null;

    public TaskQuantity addProductVariantId(String id) {
        if (this.id == null) {
            this.id = new ArrayList<>();
        }
        this.id.add(id);
        return this;
    }

}
