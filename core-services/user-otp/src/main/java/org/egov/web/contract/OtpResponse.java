package org.egov.web.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Builder
public class OtpResponse {
    private ResponseInfo responseInfo;
    @JsonProperty("isSuccessful")
    private boolean successful;
}



