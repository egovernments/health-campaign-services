package org.egov.kafka;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.service.AttendanceRegisterService;
import org.egov.service.OrganisationContactDetailsStaffUpdateService;
import org.egov.web.models.Organisation.OrgContactUpdateDiff;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Slf4j
public class Consumer {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OrganisationContactDetailsStaffUpdateService organisationContactDetailsStaffUpdateService;
    @Autowired
    private AttendanceRegisterService attendanceRegisterService;

    @KafkaListener(topics = "${organisation.contact.details.update.topic}")
    public void updateAttendanceStaff(String consumerRecord,
                                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic){
        try {
            OrgContactUpdateDiff orgContactUpdateDiff = objectMapper.readValue(consumerRecord, OrgContactUpdateDiff.class);
            organisationContactDetailsStaffUpdateService.updateStaffPermissionsForContactDetails(orgContactUpdateDiff);
        } catch(Exception e){
            log.error("Error updating staff permissions for update in organisation contact details", e);
        }
    }

    /**
     * Update end date for approved time extension request
     * @param consumerRecord
     * @param topic
     */
    @KafkaListener(topics = "${contracts.revision.topic}")
    public void updateEndDate(String consumerRecord, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            JsonNode attendanceContractRevisionRequest = objectMapper.readValue(consumerRecord, JsonNode.class);
            RequestInfo requestInfo = objectMapper.convertValue(attendanceContractRevisionRequest.get("RequestInfo"), RequestInfo.class);
            String tenantId = attendanceContractRevisionRequest.get("tenantId").asText();
            String referenceId = attendanceContractRevisionRequest.get("referenceId").asText();
            BigDecimal endDate =  attendanceContractRevisionRequest.get("endDate").decimalValue();
            attendanceRegisterService.updateEndDateForRevisedContract(requestInfo, tenantId, referenceId, endDate);
        }catch (Exception e) {
            log.error("Error end date for contract");
        }
    }

}
