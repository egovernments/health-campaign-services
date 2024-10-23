package digit.config;

import lombok.*;
import org.egov.tracer.config.TracerConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Data
@Import({TracerConfiguration.class})
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class Configuration {

    // Allowed roles for census
    @Value("#{${allowed.census.roles}}")
    private List<String> allowedCensusRoles;

    // Persister Topic
    @Value("${census.create.topic}")
    private String censusCreateTopic;

    @Value("${census.update.topic}")
    private String censusUpdateTopic;

    // Boundary Service
    @Value("${egov.boundary.service.host}")
    private String boundaryServiceHost;

    @Value("${egov.boundary.relationship.search.endpoint}")
    private String boundaryRelationshipSearchEndpoint;

    // Plan Service
    @Value("${egov.plan.service.host}")
    private String planServiceHost;

    @Value("${egov.plan.employee.assignment.search.endpoint}")
    private String planEmployeeAssignmentSearchEndpoint;

    //SMSNotification
    @Value("${egov.sms.notification.topic}")
    private String smsNotificationTopic;

    //Pagination
    @Value("${census.default.offset}")
    private Integer defaultOffset;

    @Value("${census.default.limit}")
    private Integer defaultLimit;
}
