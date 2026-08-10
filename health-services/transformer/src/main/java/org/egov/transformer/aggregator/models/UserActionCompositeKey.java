package org.egov.transformer.aggregator.models;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.validation.annotation.Validated;

@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserActionCompositeKey {

    @JsonProperty("projectId")
    private String projectId;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("clientCreatedBy")
    private String clientCreatedBy;

    @JsonProperty("clientCreatedDate")
    private String clientCreatedDate;

    // Overriding equals and hashCode to ensure proper grouping
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserActionCompositeKey that = (UserActionCompositeKey) o;
        return Objects.equals(projectId, that.projectId) &&
                Objects.equals(tenantId, that.tenantId) &&
                Objects.equals(clientCreatedBy, that.clientCreatedBy) &&
                Objects.equals(clientCreatedDate, that.clientCreatedDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectId, tenantId, clientCreatedBy, clientCreatedDate);
    }

    public String getId() {
        return clientCreatedBy+"-"+tenantId+"-"+clientCreatedDate;
    }
}
