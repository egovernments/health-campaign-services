package digit.config;

import lombok.*;
import org.egov.tracer.config.TracerConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

import java.util.List;
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

    @Value("${plan.estimation.approver.roles}")
    public List<String> planEstimationApproverRoles;

    //MDMS
    @Value("${egov.mdms.host}")
    private String mdmsHost;

    @Value("${egov.mdms.search.endpoint}")
    private String mdmsEndPoint;

    @Value("${egov.mdms.search.v2.endpoint}")
    private String mdmsV2EndPoint;

    //HRMS
    @Value("${egov.hrms.host}")
    private String hrmsHost;

    @Value("${egov.hrms.search.endpoint}")
    private String hrmsEndPoint;

    //Project Factory
    @Value("${egov.project.factory.host}")
    private String projectFactoryHost;

    @Value("${egov.project.factory.search.endpoint}")
    private String projectFactorySearchEndPoint;

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

    @Value("${plan.default.offset}")
    private Integer defaultOffset;

    @Value("${plan.default.limit}")
    private Integer defaultLimit;

    //Workflow
    @Value("${egov.workflow.host}")
    private String wfHost;

    @Value("${egov.workflow.transition.path}")
    private String wfTransitionPath;

    @Value("${workflow.initiate.action}")
    private List<String> wfInitiateActions;

    @Value("${workflow.intermediate.action}")
    private List<String> wfIntermediateActions;

    @Value("${workflow.send.back.actions}")
    private List<String> wfSendBackActions;

}
