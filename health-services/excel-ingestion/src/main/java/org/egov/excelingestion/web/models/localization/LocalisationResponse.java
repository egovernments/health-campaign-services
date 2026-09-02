package org.egov.excelingestion.web.models.localization;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LocalisationResponse {
    @JsonProperty("ResponseInfo")
    private Object responseInfo; // Can be a more specific DTO if needed

    @JsonProperty("messages")
    private List<LocalisationMessage> messages;
}
