package org.egov.web.notification.push.consumer.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryEnvelope {

    private PushNotificationRequest request;

    private int attempt;

    private long notBefore;

    private String lastError;

}
