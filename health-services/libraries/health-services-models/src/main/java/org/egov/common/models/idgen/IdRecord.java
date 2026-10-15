package org.egov.common.models.idgen;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovModel;

@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class IdRecord extends EgovModel {

    @Size(max = 200)
    @JsonProperty("status")
    private String status;


    @Override
    @EqualsAndHashCode.Include
    public String getId() {
        return super.getId();
    }

    public String getLastModifiedBy() {
        return super.auditDetails.getLastModifiedBy();
    }
}
