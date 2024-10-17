package digit.config;

import lombok.*;
import org.egov.tracer.config.TracerConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Data
@Import({TracerConfiguration.class})
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class Configuration {

    //Role Map
    @Value("#{${role.map}}")
    public Map<String, String> roleMap;

    //MDMS
    @Value("${egov.mdms.host}")
    private String mdmsHost;

    @Value("${egov.mdms.search.endpoint}")
    private String mdmsEndPoint;

    @Value("${egov.mdms.search.v2.endpoint}")
    private String mdmsV2EndPoint;

    //Project Factory
    @Value("${egov.project.factory.host}")
    private String projectFactoryHost;

    @Value("${egov.project.factory.search.endpoint}")
    private String projectFactorySearchEndPoint;

    //User Service
    @Value("${egov.user.service.host}")
    private String userServiceHost;

    @Value("${egov.user.search.endpoint}")
    private String userSearchEndPoint;

    //Persister Topic
    @Value("${plan.configuration.create.topic}")
    private String planConfigCreateTopic;

    @Value("${plan.configuration.update.topic}")
    private String planConfigUpdateTopic;

    @Value("${plan.employee.assignment.create.topic}")
    private String planEmployeeAssignmentCreateTopic;

    @Value("${plan.employee.assignment.update.topic}")
    private String planEmployeeAssignmentUpdateTopic;

    @Value("${plan.create.topic}")
    private String planCreateTopic;

    @Value("${plan.update.topic}")
    private String planUpdateTopic;

    @Value("${plan.facility.create.topic}")
    private String planFacilityCreateTopic;

    @Value("${plan.facility.update.topic}")
    private String planFacilityUpdateTopic;

    @Value("${plan.default.offset}")
    private Integer defaultOffset;

    @Value("${plan.default.limit}")
    private Integer defaultLimit;

    //Facility
    @Value("${egov.facility.host}")
    private String facilityHost;

    @Value("${egov.facility.search.endpoint}")
    private String facilitySearchEndPoint;

    //Workflow
    @Value("${egov.workflow.host}")
    private String wfHost;

    @Value("${egov.workflow.transition.path}")
    private String wfTransitionPath;

}
