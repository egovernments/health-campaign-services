package org.egov.healthnotification.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.Error;
import org.egov.healthnotification.Constants;
import org.egov.healthnotification.config.HealthNotificationProperties;
import org.egov.healthnotification.repository.ScheduledNotificationRepository;
import org.egov.healthnotification.service.NotificationDispatchService;
import org.egov.healthnotification.service.NotificationEncryptionService;
import org.egov.healthnotification.web.models.ScheduledNotification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Component
@Slf4j
@ConditionalOnProperty(name = "notification.scheduler.enabled", havingValue = "true", matchIfMissing = false)
public class NotificationScheduler {

    private final ScheduledNotificationRepository repository;
    private final NotificationDispatchService dispatchService;
    private final HealthNotificationProperties properties;
    private final NotificationEncryptionService encryptionService;

    @Autowired
    public NotificationScheduler(ScheduledNotificationRepository repository,
                                 NotificationDispatchService dispatchService,
                                 HealthNotificationProperties properties,
                                 NotificationEncryptionService encryptionService) {
        this.repository = repository;
        this.dispatchService = dispatchService;
        this.properties = properties;
        this.encryptionService = encryptionService;
    }

    /**
     * CRON job entry point.
     *
     * Fetches all due notifications in one DB call, then dispatches in batches.
     * Status updates go through Kafka (async), so we don't re-query — we process
     * the full list fetched at the start of this run.
     */
    @Scheduled(cron = "${notification.scheduler.cron:0 0 8 * * *}",
               zone = "${notification.timezone:UTC}")
    public void processScheduledNotifications() {
        ZoneId timezone = ZoneId.of(properties.getNotificationTimezone());
        LocalDate today = LocalDate.now(timezone);
        int batchSize = properties.getSchedulerBatchSize();
        String tenantId = properties.getStateLevelTenantId();

        log.info("Scheduler triggered. today={} (timezone={}), batchSize={}, tenantId={}",
                today, timezone, batchSize, tenantId);

        try {
            // Fetch due notifications, capped to prevent OOM on large backlogs.
            // Remaining rows (if any) will be picked up on the next scheduled run.
            int maxFetch = properties.getSchedulerMaxFetch();
            List<ScheduledNotification> allDue = repository.fetchPendingNotifications(
                    tenantId, today, maxFetch);

            if (allDue.isEmpty()) {
                log.info("No pending notifications due for today: {}", today);
                return;
            }

            log.info("Found {} pending notifications due for scheduledAt <= {}", allDue.size(), today);

            // Decrypt PII data (mobileNumber and contextData) after fetching
            RequestInfo requestInfo = RequestInfo.builder().build();
            allDue = encryptionService.decrypt(allDue, Constants.ENCRYPTION_KEY_SCHEDULED_NOTIFICATION, requestInfo);
            log.info("Decrypted {} scheduled notifications", allDue.size());

            // Process in batches — one batch failure does not stop subsequent batches
            int totalBatches = (allDue.size() + batchSize - 1) / batchSize;
            Map<ScheduledNotification, List<Error>> allErrors = new HashMap<>();
            int batchFailures = 0;

            for (int i = 0; i < allDue.size(); i += batchSize) {
                int end = Math.min(i + batchSize, allDue.size());
                List<ScheduledNotification> batch = allDue.subList(i, end);
                int batchNumber = (i / batchSize) + 1;

                try {
                    log.info("Processing batch {}/{} ({} notifications)", batchNumber, totalBatches, batch.size());
                    Map<ScheduledNotification, List<Error>> batchErrors =
                            dispatchService.dispatchBatch(batch, tenantId);
                    allErrors.putAll(batchErrors);
                } catch (Exception e) {
                    batchFailures++;
                    log.error("Batch {}/{} failed entirely: {}", batchNumber, totalBatches, e.getMessage(), e);
                    // Continue to next batch — do not stop the scheduler run
                }
            }

            log.info("Scheduler completed. Processed {} notifications in {} batches. " +
                            "Errors: {} notification(s), {} batch failure(s)",
                    allDue.size(), totalBatches, allErrors.size(), batchFailures);

        } catch (Exception e) {
            log.error("Scheduler failed for tenantId: {}, today: {}", tenantId, today, e);
        }
    }
}
