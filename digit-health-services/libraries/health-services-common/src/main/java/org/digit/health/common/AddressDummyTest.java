package org.digit.health.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressDummyTest {

    @JsonProperty("addressId")
    private String addressId;

    @JsonProperty("addressText")
    private String addressText;
}
