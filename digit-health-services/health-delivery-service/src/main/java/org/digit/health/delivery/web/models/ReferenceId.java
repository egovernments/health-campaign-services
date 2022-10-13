package org.digit.health.delivery.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReferenceId {

    @JsonProperty("id")
    private String id;

    @JsonProperty("type")
    private String type;
}
