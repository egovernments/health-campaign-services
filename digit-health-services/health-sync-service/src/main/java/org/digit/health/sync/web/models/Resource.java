package org.digit.health.sync.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Resource {

    @JsonProperty("resourceId")
    private String resourceId;

    @JsonProperty("quantityToBeDelivered")
    private int quantityToBeDelivered;

    @JsonProperty("quantityDelivered")
    private int quantityDelivered;

    @JsonProperty("reasonIfUndelivered")
    private String reasonIfUndelivered;

    @JsonProperty("isDelivered")
    private boolean isDelivered;
}
