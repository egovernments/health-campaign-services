package org.egov.individual.web.models.register;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IndividualRegister {
    @NotBlank(message = "Name is required")
    String name;

    String emailId;

    String mobileNumber;

    String requestType;

    @NotBlank(message = "Tenant ID is required")
    String tenantId;

    String otp;
}
