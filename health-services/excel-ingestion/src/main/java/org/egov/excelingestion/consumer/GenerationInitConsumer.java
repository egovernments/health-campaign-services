package org.egov.excelingestion.consumer;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.producer.Producer;
import org.egov.excelingestion.config.KafkaTopicConfig;
import org.egov.excelingestion.constants.GenerationConstants;
import org.egov.excelingestion.repository.GeneratedFileRepository;
import org.egov.excelingestion.service.AsyncGenerationService;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.GenerateResourceRequest;
import org.egov.excelingestion.web.models.GenerationSearchCriteria;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Listens on the generation init topic and drives a single record through the
 * IN_PROGRESS / COMPLETED / FAILED lifecycle. The listener uses
 * max.poll.records = 1 and manual immediate ack (so a single record is in flight
 * per partition and the offset is committed only once we are done with it).
 *
 * Row-readiness: the QUEUED row is created by an external persister from a
 * SEPARATE save topic, so there is no ordering/latency guarantee between that
 * save and this init event. If the row has not materialized within the wait
 * window we RE-QUEUE the init event (bounded retries) so it is reprocessed once
 * the row exists. Re-queueing is used instead of (a) silently dropping the event
 * (which left the row stuck QUEUED) or (b) publishing a FAILED update, which
 * races with - and is overwritten by - the late save and is therefore
 * unreliable. Only after the retries are exhausted do we write a terminal FAILED
 * as a best-effort recovery signal for the polling client.
 *
 * Pipeline failures (an exception from processGeneration) write FAILED to the
 * row and ack; we deliberately do NOT redeliver on those - retry is user-driven
 * via a fresh /generate/_init, which expires the prior record and supersedes it.
 */
@Component
@Slf4j
public class GenerationInitConsumer {

    // Internal marker carried on the init event (not persisted) to bound retries.
    private static final String INIT_ATTEMPT_KEY = "__initAttempt__";

    private final AsyncGenerationService asyncGenerationService;
    private final GeneratedFileRepository generatedFileRepository;
    private final Producer producer;
    private final KafkaTopicConfig kafkaTopicConfig;

    @Value("${excel.ingestion.generation.row.wait.timeout.ms:10000}")
    private long rowWaitTimeoutMs;

    @Value("${excel.ingestion.generation.row.wait.interval.ms:250}")
    private long rowWaitIntervalMs;

    @Value("${excel.ingestion.generation.init.max.attempts:3}")
    private int maxInitAttempts;

    public GenerationInitConsumer(AsyncGenerationService asyncGenerationService,
                                  GeneratedFileRepository generatedFileRepository,
                                  Producer producer,
                                  KafkaTopicConfig kafkaTopicConfig) {
        this.asyncGenerationService = asyncGenerationService;
        this.generatedFileRepository = generatedFileRepository;
        this.producer = producer;
        this.kafkaTopicConfig = kafkaTopicConfig;
    }

    @KafkaListener(topics = "${excel.ingestion.generation.init.topic}")
    public void consume(GenerateResourceRequest event, Acknowledgment acknowledgment) {
        if (event == null || event.getGenerateResource() == null) {
            log.warn("Received null/invalid generation init event; acking and skipping");
            acknowledgment.acknowledge();
            return;
        }

        GenerateResource generateResource = event.getGenerateResource();
        String id = generateResource.getId();
        String tenantId = generateResource.getTenantId();

        log.info("Consumed generation init event id={} tenantId={} referenceId={} type={}",
                id, tenantId, generateResource.getReferenceId(), generateResource.getType());

        try {
            // Persister-driven save happens on a different topic. Wait briefly for the
            // row to materialize so the IN_PROGRESS update has something to update.
            GenerateResource currentRecord = waitForRow(generateResource);
            if (currentRecord == null) {
                handleRowNotMaterialized(event, generateResource, id, tenantId, acknowledgment);
                return;
            }

            // Row found - drop the internal retry marker so it is not persisted into the row.
            clearInitAttempt(generateResource);

            if (!GenerationConstants.STATUS_QUEUED.equalsIgnoreCase(currentRecord.getStatus())) {
                // A newer init already superseded this record (expired it) - skip.
                log.info("Generation id={} no longer queued (status={}); skipping", id, currentRecord.getStatus());
                acknowledgment.acknowledge();
                return;
            }

            asyncGenerationService.processGeneration(generateResource, event.getRequestInfo());
        } catch (Exception e) {
            // processGeneration is expected to handle its own failures and write FAILED;
            // this catch is for anything that escapes (e.g. wait failure).
            log.error("Unhandled error processing generation init event id={}: {}", id, e.getMessage(), e);
        } finally {
            acknowledgment.acknowledge();
        }
    }

    /**
     * The QUEUED row did not appear in time (persister lag / cross-topic ordering). Re-queue
     * the init event so it is retried once the row exists; this avoids the FAILED-update-vs-late-save
     * race. Only after exhausting the retry budget do we write a terminal FAILED as a best-effort signal.
     */
    private void handleRowNotMaterialized(GenerateResourceRequest event, GenerateResource generateResource,
                                          String id, String tenantId, Acknowledgment acknowledgment) {
        int attempt = readInitAttempt(generateResource);
        if (attempt < maxInitAttempts) {
            int next = attempt + 1;
            setInitAttempt(generateResource, next);
            log.warn("Generation row for id={} not materialized yet; re-queuing init (attempt {}/{})",
                    id, next, maxInitAttempts);
            try {
                producer.push(tenantId, kafkaTopicConfig.getGenerationInitTopic(), event);
            } catch (Exception ex) {
                log.error("Failed to re-queue init event for id={}: {}", id, ex.getMessage(), ex);
            }
            acknowledgment.acknowledge();
            return;
        }

        log.warn("Generation row for id={} never appeared after {} attempts; marking FAILED", id, maxInitAttempts);
        try {
            clearInitAttempt(generateResource);
            asyncGenerationService.markFailed(generateResource, event.getRequestInfo(),
                    GenerationConstants.GENERATION_ROW_NOT_MATERIALIZED,
                    GenerationConstants.GENERATION_ROW_NOT_MATERIALIZED_MESSAGE);
        } catch (Exception ex) {
            log.error("Failed to publish FAILED status for generation id={}: {}", id, ex.getMessage(), ex);
        }
        acknowledgment.acknowledge();
    }

    private int readInitAttempt(GenerateResource resource) {
        Map<String, Object> ad = resource.getAdditionalDetails();
        if (ad != null && ad.get(INIT_ATTEMPT_KEY) instanceof Number) {
            return ((Number) ad.get(INIT_ATTEMPT_KEY)).intValue();
        }
        return 1;
    }

    private void setInitAttempt(GenerateResource resource, int attempt) {
        Map<String, Object> ad = resource.getAdditionalDetails();
        if (ad == null) {
            ad = new HashMap<>();
            resource.setAdditionalDetails(ad);
        }
        ad.put(INIT_ATTEMPT_KEY, attempt);
    }

    private void clearInitAttempt(GenerateResource resource) {
        if (resource.getAdditionalDetails() != null) {
            resource.getAdditionalDetails().remove(INIT_ATTEMPT_KEY);
        }
    }

    private GenerateResource waitForRow(GenerateResource expected) {
        long deadline = System.currentTimeMillis() + rowWaitTimeoutMs;
        GenerationSearchCriteria criteria = GenerationSearchCriteria.builder()
                .tenantId(expected.getTenantId())
                .ids(Collections.singletonList(expected.getId()))
                .build();

        while (System.currentTimeMillis() < deadline) {
            try {
                List<GenerateResource> rows = generatedFileRepository.search(criteria);
                if (rows != null && !rows.isEmpty()) {
                    return rows.get(0);
                }
            } catch (InvalidTenantIdException e) {
                log.error("Invalid tenant id while waiting for generation row id={}: {}",
                        expected.getId(), e.getMessage());
                return null;
            } catch (Exception e) {
                log.warn("Error polling for generation row id={}: {}", expected.getId(), e.getMessage());
            }

            try {
                Thread.sleep(rowWaitIntervalMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }
}
