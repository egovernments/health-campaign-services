package org.egov.fhirtransformer.repository;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hl7.fhir.r5.model.Bundle;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.Collectors;

/**
 * Kafka producer service for publishing FHIR processing failures.
 */
@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final FhirContext ctx;

    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);

    @Value("${kafka.dlq.topic}")
    private String dlqTopic;

    @Value("${kafka.failed.topic}")
    private String failedTopic;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate, FhirContext ctx) {
        this.kafkaTemplate = kafkaTemplate;
        this.ctx = ctx;
    }

    /**
     * Publishes FHIR bundle validation errors to the Dead Letter Queue (DLQ).
     *
     * <p>The published message contains:
     * <ul>
     *   <li>Bundle identifier</li>
     *   <li>Timestamp of failure</li>
     *   <li>Original FHIR payload</li>
     *   <li>List of validation error messages</li>
     * </ul>
     *
     * @param result validation result containing FHIR validation messages
     * @param bundleId identifier of the processed FHIR bundle
     * @param fhirJson original FHIR payload as JSON
     * @throws JsonProcessingException if message serialization fails
     */
    public void publishToDLQ(ValidationResult result, String bundleId, JsonNode fhirJson) throws JsonProcessingException {

        List<String> errorList = result.getMessages().stream()
                .filter(msg -> msg.getSeverity() == ResultSeverityEnum.ERROR)
                .map(SingleValidationMessage::getMessage)
                .collect(Collectors.toList());
        ObjectMapper mapper = new ObjectMapper();
        String jsonArray = mapper.writeValueAsString(errorList);

        ObjectNode dlqJson = mapper.createObjectNode();
        dlqJson.put("id", bundleId);
        dlqJson.put("timestamp", Instant.now().toString());
        dlqJson.put("fhirPayload", fhirJson);
        dlqJson.set("errors", mapper.readTree(jsonArray));

        String finalJson = mapper.writeValueAsString(dlqJson);
        logger.info(finalJson);
        // Publish to Kafka
        kafkaTemplate.send(dlqTopic, bundleId, finalJson);
    }


    /**
     * Publishes individual FHIR resource processing failures to the failed topic.
     * <p>The published message contains:
     * <ul>
     *   <li>FHIR resource ID</li>
     *   <li>FHIR resource type</li>
     *   <li>FHIR resource payload</li>
     *   <li>Error reason for the failure</li>
     * </ul>
     * @param entry bundle entry containing the failed FHIR resource
     * @param errorMessage reason for the resource processing failure
     * @throws RuntimeException if message serialization fails
     */
    public void publishFhirResourceFailures(Bundle.BundleEntryComponent entry, String errorMessage) {

        String finalJson;
        String resourceId = entry.getResource().getIdElement().getIdPart();
        String resourceType = entry.getResource().fhirType();
        String resourceJson = ctx.newJsonParser().encodeResourceToString(entry.getResource());

        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode failedResourceJson = objectMapper.createObjectNode();

        failedResourceJson.put("resourceId", resourceId);
        failedResourceJson.put("resourceType", resourceType);
        failedResourceJson.put("fhirResource", resourceJson);
        failedResourceJson.put("errorReason", errorMessage);

        try {
            finalJson = objectMapper.writeValueAsString(failedResourceJson);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to publish to Failed Topic", e);
        }
        // Publish to Kafka
        kafkaTemplate.send(failedTopic, resourceId, finalJson);
    }
}