package org.egov.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.ProjectStaffBulkRequest;
import org.egov.util.ProjectStaffUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class ProjectStaffConsumer {

    @Autowired
    private  ObjectMapper objectMapper;

    @Autowired
    private ProjectStaffUtil projectStaffUtil;

    @KafkaListener(topics = "${project.staff.consumer.bulk.create.topic}, ${project.staff.consumer.bulk.create.topic}")
    public void bulkCreate(Map<String, Object> consumerRecord,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            ProjectStaffBulkRequest request = objectMapper.convertValue(consumerRecord, ProjectStaffBulkRequest.class);
            projectStaffUtil.createRegistryForSupervisor(request);
        } catch (Exception exception) {
            log.error("error in project staff consumer bulk create", exception);
        }
    }


}
