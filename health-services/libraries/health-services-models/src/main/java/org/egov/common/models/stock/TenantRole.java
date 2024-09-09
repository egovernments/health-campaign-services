package org.egov.common.models.stock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
* User role carries the tenant related role information for the user. A user can have multiple roles per tenant based on the need of the tenant. A user may also have multiple roles for multiple tenants.
*/
    @ApiModel(description = "User role carries the tenant related role information for the user. A user can have multiple roles per tenant based on the need of the tenant. A user may also have multiple roles for multiple tenants.")
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
public class TenantRole   {
        @JsonProperty("tenantId")
      @NotNull



    private String tenantId = null;

        @JsonProperty("roles")
      @NotNull

  @Valid


    private List<org.egov.common.contract.request.Role> roles = new ArrayList<>();


        public TenantRole addRolesItem(org.egov.common.contract.request.Role rolesItem) {
        this.roles.add(rolesItem);
        return this;
        }

}

