package org.digit.health.registration.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {

    @JsonProperty("addressId")
    private String addressId;

    @JsonProperty("addressText")
    private String addressText;
}
