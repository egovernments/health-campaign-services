package org.digit.health.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeliveryDummyTest {
    @JsonProperty("deliveryId")
    int deliveryId;

    @JsonProperty("deliveryStatus")
    int deliveryStatus;
}
