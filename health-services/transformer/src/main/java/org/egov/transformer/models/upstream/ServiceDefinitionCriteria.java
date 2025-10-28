package org.egov.transformer.models.upstream;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * The object will contain all the search parameters for Service Definition.
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ServiceDefinitionCriteria {
    @JsonProperty("tenantId")
    private String tenantId = null;

    @JsonProperty("ids")
    private List<String> ids = null;

    @JsonProperty("code")
    private List<String> code = null;

    @JsonProperty("clientId")
    private String clientId = null;


    public ServiceDefinitionCriteria addIdsItem(String idsItem) {
        if (this.ids == null) {
            this.ids = new ArrayList<>();
        }
        this.ids.add(idsItem);
        return this;
    }

    public ServiceDefinitionCriteria addCodeItem(String codeItem) {
        if (this.code == null) {
            this.code = new ArrayList<>();
        }
        this.code.add(codeItem);
        return this;
    }

}
