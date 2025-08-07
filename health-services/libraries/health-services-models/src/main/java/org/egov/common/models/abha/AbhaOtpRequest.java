package org.egov.common.models.abha;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class AbhaOtpRequest {

    @JsonProperty("aadhaarNumber")
    @NotNull
    private String aadhaarNumber;
}
