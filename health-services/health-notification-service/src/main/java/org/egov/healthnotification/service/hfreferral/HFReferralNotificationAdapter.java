package org.egov.healthnotification.service.hfreferral;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.healthnotification.web.models.NotificationEvent;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Translates HFReferral events into generic NotificationEvent(s).
 *
 * STUB: Full implementation will be added once HFReferral notification
 * requirements are finalized. The consumer routes HFReferral-schema
 * records here based on additionalFields.schema = "HFReferral".
 *
 * Expected HFReferral additionalFields:
 *   - hfCoordinator, dateOfEvaluation, nameOfReferral, age, gender, cycle
 *
 * Expected HFReferral fields:
 *   - projectId, symptom, symptomSurveyId, beneficiaryId, referralCode
 */
@Service
@Slf4j
public class HFReferralNotificationAdapter {

    /**
     * Builds NotificationEvent(s) from an HFReferral record.
     *
     * @param record The raw JSON record with schema "HFReferral"
     * @param topic  The Kafka topic it came from
     * @return List of NotificationEvents (currently empty — stub)
     */
    public List<NotificationEvent> buildNotificationEvents(JsonNode record, String topic) {
        String recordId = record.path("id").asText("unknown");
        String clientReferenceId = record.path("clientReferenceId").asText("unknown");

        log.info("HFReferral notification adapter invoked for record id={}, clientReferenceId={}, topic={}. " +
                "Implementation pending.", recordId, clientReferenceId, topic);

        // TODO: Implement HFReferral notification logic
        //  1. Extract projectId, symptom, beneficiaryId from record
        //  2. Fetch MDMS config for HFReferral notifications
        //  3. Resolve recipient (hfCoordinator or facility users)
        //  4. Build placeholders from additionalFields (nameOfReferral, age, gender, cycle)
        //  5. Return NotificationEvent(s)

        return List.of();
    }
}
