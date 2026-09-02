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
 * The object will contain all the search parameters for Service .
 */
@Schema(description = "The object will contain all the search parameters for Service .")
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ServiceCriteria {
    @JsonProperty("tenantId")
    @NotNull
    @Size(min = 2, max = 64)
    private String tenantId = null;

    @JsonProperty("ids")
    private List<String> ids = null;

    @JsonProperty("serviceDefIds")
    private List<String> serviceDefIds = null;

    @JsonProperty("referenceIds")
    private List<String> referenceIds = null;

    @JsonProperty("accountId")
    private String accountId = null;

    @JsonProperty("clientId")
    private String clientId = null;


    public ServiceCriteria addIdsItem(String idsItem) {
        if (this.ids == null) {
            this.ids = new ArrayList<>();
        }
        this.ids.add(idsItem);
        return this;
    }

    public ServiceCriteria addServiceDefIdsItem(String serviceDefIdsItem) {
        if (this.serviceDefIds == null) {
            this.serviceDefIds = new ArrayList<>();
        }
        this.serviceDefIds.add(serviceDefIdsItem);
        return this;
    }

    public ServiceCriteria addReferenceIdsItem(String referenceIdsItem) {
        if (this.referenceIds == null) {
            this.referenceIds = new ArrayList<>();
        }
        this.referenceIds.add(referenceIdsItem);
        return this;
    }

}
