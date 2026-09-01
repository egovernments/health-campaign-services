package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.project.ProjectStaff;
import org.egov.transformer.transformationservice.ProjectStaffTransformationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class ProjectStaffConsumer {


    private final ObjectMapper objectMapper;
    private final ProjectStaffTransformationService projectStaffTransformationService;

    @Autowired
    public ProjectStaffConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper, ProjectStaffTransformationService projectStaffTransformationService) {
        this.objectMapper = objectMapper;
        this.projectStaffTransformationService = projectStaffTransformationService;
    }

    // The delete topic is consumed so that a RETIRED assignment is tombstoned in the index instead of
    // surviving as a live document. A worker whose area is corrected gets a NEW staff row (new id), and
    // without this subscription the old document stays isDeleted=false forever - so the worker is counted
    // twice on every dashboard that reads this index. The delete message carries the soft-deleted entity
    // with isDeleted=true and the transformation already copies that flag through, so subscribing is the
    // whole fix. The document id is unchanged ($.id), so the tombstone upserts in place.
    @KafkaListener(topics = {"${transformer.consumer.bulk.create.project.staff.topic}",
            "${transformer.consumer.bulk.update.project.staff.topic}",
            "${transformer.consumer.bulk.delete.project.staff.topic}"})
    public void consumeStaff(ConsumerRecord<String, Object> payload,
                             @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<ProjectStaff> payloadList = Arrays.asList(objectMapper
                    .readValue((String) payload.value(),
                            ProjectStaff[].class));
            projectStaffTransformationService.transform(payloadList);
        } catch (Exception exception) {
            log.error("TRANSFORMER error in projectStaff consumer {}", ExceptionUtils.getStackTrace(exception));
        }
    }
}
