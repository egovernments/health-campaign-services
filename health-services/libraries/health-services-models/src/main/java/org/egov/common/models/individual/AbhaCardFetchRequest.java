package org.egov.common.models.individual;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.egov.common.contract.request.RequestInfo;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbhaCardFetchRequest {

    @JsonProperty("RequestInfo")
    @NotNull(message = "RequestInfo cannot be null")
    private RequestInfo requestInfo;

    @JsonProperty("abhaNumber")
    @NotBlank(message = "abhaNumber cannot be blank")
    private String abhaNumber;

    @JsonProperty("cardType")
    @NotBlank(message = "cardType is mandatory")
    @Pattern(
            regexp = "getCard|getSvgCard|getPngCard",
            message = "cardType must be one of: getCard, getSvgCard, getPngCard"
    )
    private String cardType;
}
