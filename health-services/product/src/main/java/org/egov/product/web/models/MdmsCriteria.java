package org.egov.product.web.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.mdms.ModuleDetail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MdmsCriteria
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-05-30T09:26:57.838+05:30[Asia/Kolkata]")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MdmsCriteria {
    @JsonProperty("tenantId")
    @Size(min = 1, max = 100)
    @org.jetbrains.annotations.NotNull
    private String tenantId = null;

    @JsonProperty("ids")
    private Set<String> ids = null;

    @JsonProperty("uniqueIdentifier")
    @Size(min = 1, max = 64)
    private String uniqueIdentifier = null;

    @JsonProperty("moduleDetails")
    @Valid
    @NotNull
    private List<ModuleDetail> moduleDetails = null;

    @JsonIgnore
    private Map<String, String> schemaCodeFilterMap = null;

    @JsonIgnore
    private Boolean isActive = Boolean.TRUE;


    public MdmsCriteria addModuleDetailsItem(ModuleDetail moduleDetailsItem) {
        if (this.moduleDetails == null) {
            this.moduleDetails = new ArrayList<>();
        }
        this.moduleDetails.add(moduleDetailsItem);
        return this;
    }

}
