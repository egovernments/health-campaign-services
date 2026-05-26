package org.egov.excelingestion.consumer;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
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
import java.util.List;

/**
 * Listens on the generation init topic and drives a single record through the
 * IN_PROGRESS / COMPLETED / FAILED lifecycle. Configured (via
 * {@code generationInitListenerContainerFactory}) with:
 *   - max.poll.records = 1  (one event in flight per partition)
 *   - manual immediate ack  (we ack only after the run lands a terminal status)
 *
 * Failure handling: an exception from the generation pipeline writes FAILED to
 * the DB/cache and then acks the offset. We deliberately do NOT redeliver on
 * failure - retry is user-driven via a fresh /generate/_init call, which
 * expires the prior record and supersedes it.
 */
@Component
@Slf4j
public class GenerationInitConsumer {

    private final AsyncGenerationService asyncGenerationService;
    private final GeneratedFileRepository generatedFileRepository;

    @Value("${excel.ingestion.generation.row.wait.timeout.ms:10000}")
    private long rowWaitTimeoutMs;

    @Value("${excel.ingestion.generation.row.wait.interval.ms:250}")
    private long rowWaitIntervalMs;

    public GenerationInitConsumer(AsyncGenerationService asyncGenerationService,
                                  GeneratedFileRepository generatedFileRepository) {
        this.asyncGenerationService = asyncGenerationService;
        this.generatedFileRepository = generatedFileRepository;
    }

    @KafkaListener(
            topics = "${excel.ingestion.generation.init.topic}",
            groupId = "${spring.kafka.consumer.group-id:excel-ingestion}",
            containerFactory = "generationInitListenerContainerFactory"
    )
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
                log.warn("Generation row for id={} never appeared; acking and dropping event", id);
                acknowledgment.acknowledge();
                return;
            }

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
