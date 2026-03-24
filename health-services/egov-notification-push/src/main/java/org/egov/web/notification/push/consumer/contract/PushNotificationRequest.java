package org.egov.web.notification.push.consumer.contract;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@Builder
@Getter
@NoArgsConstructor
@Setter
@ToString
public class PushNotificationRequest {

    private String title;

    private String body;

    private Map<String, String> data;

    private List<String> deviceTokens;

    //Added facilityId — when present, the listener resolves device tokens from DB by facilityId
    private String facilityId;

    private String tenantId;

}
