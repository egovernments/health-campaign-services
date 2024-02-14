package org.egov.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.core.Role;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualSearch;
import org.egov.common.models.project.ProjectStaff;
import org.egov.common.models.project.ProjectStaffRequest;
import org.egov.config.AttendanceServiceConfiguration;
import org.egov.tracer.model.CustomException;
import org.egov.util.IndividualServiceUtil;
import org.egov.util.ProjectStaffUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ProjectStaffConsumer {

    @Autowired
    private  ObjectMapper objectMapper;

    @Autowired
    private ProjectStaffUtil projectStaffUtil;

    @Autowired
    private IndividualServiceUtil individualServiceUtil;

    @Autowired
    private AttendanceServiceConfiguration config;

    @KafkaListener(topics = {"${project.staff.kafka.create.topic}"})
    public void bulkCreate(Map<String, Object> consumerRecord,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            log.info("Listening to topic "+ topic);
            ProjectStaffRequest request = objectMapper.convertValue(consumerRecord, ProjectStaffRequest.class);
            ProjectStaff projectStaff = request.getProjectStaff();

            try {
                RequestInfo requestInfo = request.getRequestInfo();

                String staffUserUuid = projectStaff.getUserId();
                String tenantId = projectStaff.getTenantId();

                IndividualSearch individualSearch = IndividualSearch.builder().userUuid(staffUserUuid).build();
                List<Individual> individualList = individualServiceUtil.getIndividualDetailsFromSearchCriteria(individualSearch, requestInfo, tenantId);

                if (individualList.isEmpty())
                    throw new CustomException("INVALID_STAFF_ID", "No Individual found for the given staff Uuid - " + staffUserUuid);
                Individual individual = individualList.get(0);

                List<Role> roleList = individual.getUserDetails().getRoles();
                List<String> roleCodeList = roleList.stream()
                        .map(Role::getCode)
                        .collect(Collectors.toList());

                boolean matchFoundForSupervisorRoles = roleCodeList.stream()
                        .anyMatch(config.getProjectSupervisorRoles()::contains);

                boolean matchFoundForAttendeeRoles = roleCodeList.stream()
                        .anyMatch(config.getProjectAttendeeRoles()::contains);

                if (matchFoundForSupervisorRoles)
                    projectStaffUtil.createRegistryForSupervisor(projectStaff, requestInfo, individual);

                if (matchFoundForAttendeeRoles)
                    projectStaffUtil.enrollAttendeetoRegister(projectStaff, requestInfo, individual);
            }
            catch (Exception e)
            {
                log.error(e.toString());
            }

        } catch (Exception exception) {
            log.error("error in project staff consumer bulk create", exception);
        }
    }


}
