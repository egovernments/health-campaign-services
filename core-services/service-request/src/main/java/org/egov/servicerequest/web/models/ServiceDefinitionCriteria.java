package org.egov.servicerequest.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * The object will contain all the search parameters for Service Definition.
 */
@Schema(description = "The object will contain all the search parameters for Service Definition.")
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ServiceDefinitionCriteria {
    @JsonProperty("tenantId")
    @NotNull
    @Size(min = 2, max = 64)
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
