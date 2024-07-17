package org.egov.common.models.individual;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.user.enums.UserType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.models.core.Role;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDetails {
    @Size(max=180)
    @JsonProperty("username")
    private String username;
    @Size(max=64)
    @JsonProperty("password")
    private String password;
    @Size(min = 2, max = 1000)
    @JsonProperty("tenantId")
    private String tenantId;
    @JsonProperty("roles")
    @Valid
    private List<Role> roles;
    @Size(max=50)
    @JsonProperty("type")
    private UserType userType;
}
