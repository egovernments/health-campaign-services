package digit.config;

import lombok.*;
import org.egov.tracer.config.TracerConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

@Component
@Data
@Import({TracerConfiguration.class})
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class Configuration {

    //Persister Topic
    @Value("${census.create.topic}")
    private String censusCreateTopic;

    @Value("${census.update.topic}")
    private String censusUpdateTopic;

    //SMSNotification
    @Value("${egov.sms.notification.topic}")
    private String smsNotificationTopic;

    //Pagination
    @Value("${census.default.offset}")
    private Integer defaultOffset;

    @Value("${census.default.limit}")
    private Integer defaultLimit;
}
