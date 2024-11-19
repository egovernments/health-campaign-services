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

    @Value("#{${workflow.restricted.roles}}")
    private List<String> workflowRestrictedRoles;

    // Persister Topic
    @Value("${census.create.topic}")
    private String censusCreateTopic;

    @Value("${census.update.topic}")
    private String censusUpdateTopic;

    @Value("${census.bulk.update.topic}")
    private String censusBulkUpdateTopic;

    @Value("${plan.facility.update.topic}")
    private String planFcailityUpdateTopic;

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

    //Workflow
    @Value("${egov.workflow.host}")
    private String wfHost;

    @Value("${egov.workflow.transition.path}")
    private String wfTransitionPath;

    @Value("${egov.business.service.search.endpoint}")
    private String businessServiceSearchEndpoint;

    @Value("${workflow.initiate.action}")
    private List<String> wfInitiateActions;

    @Value("${workflow.intermediate.action}")
    private List<String> wfIntermediateActions;

    @Value("${workflow.send.back.actions}")
    private List<String> wfSendBackActions;

    //SMSNotification
    @Value("${egov.sms.notification.topic}")
    private String smsNotificationTopic;

    //Pagination
    @Value("${census.default.offset}")
    private Integer defaultOffset;

    @Value("${census.default.limit}")
    private Integer defaultLimit;
}
