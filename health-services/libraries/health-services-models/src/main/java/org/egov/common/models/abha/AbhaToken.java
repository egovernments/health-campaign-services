package org.egov.common.models.abha;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class AbhaToken {

    @JsonProperty("token")
    private String token;

    @JsonProperty("expiresIn")
    private int expiresIn;

    @JsonProperty("refreshToken")
    private String refreshToken;

    @JsonProperty("refreshExpiresIn")
    private int refreshExpiresIn;
}
