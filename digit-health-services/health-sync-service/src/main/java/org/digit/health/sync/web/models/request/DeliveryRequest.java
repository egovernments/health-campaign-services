package org.digit.health.sync.web.models.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeliveryRequest {
    private String clientReferenceId;
    private String registrationClientReferenceId;
}
