package org.egov.healthnotification.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.egov.healthnotification.config.HealthNotificationProperties;
import org.egov.healthnotification.repository.ScheduledNotificationRepository;
import org.egov.healthnotification.service.NotificationDispatchService;
import org.egov.healthnotification.web.models.ScheduledNotification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Configurable CRON-based scheduler that picks up due notifications and dispatches them.
 *
 * Runs at the time configured by {@code notification.scheduler.cron} (default: daily 8 AM).
 * Uses the configured {@code notification.timezone} for determining "today" — never JVM default.
 *
 * Fetches PENDING notifications where scheduledAt <= today (capped at scheduler.max.fetch to
 * prevent OOM on large backlogs), then processes them in configurable batch sizes
 * via {@link NotificationDispatchService}.
 *
 * Note: Status updates go through Kafka->persister (async), so we cannot re-query between
 * batches — the DB would return the same rows. Instead we fetch once and split in-memory.
 * If more rows exist beyond max.fetch, they'll be picked up on the next scheduled run.
 *
 * Enabled/disabled via {@code notification.scheduler.enabled} property.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "notification.scheduler.enabled", havingValue = "true", matchIfMissing = false)
public class NotificationScheduler {

    private final ScheduledNotificationRepository repository;
    private final NotificationDispatchService dispatchService;
    private final HealthNotificationProperties properties;

    @Autowired
    public NotificationScheduler(ScheduledNotificationRepository repository,
                                 NotificationDispatchService dispatchService,
                                 HealthNotificationProperties properties) {
        this.repository = repository;
        this.dispatchService = dispatchService;
        this.properties = properties;
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

            // Process in batchesA
            int totalBatches = (allDue.size() + batchSize - 1) / batchSize;
            for (int i = 0; i < allDue.size(); i += batchSize) {
                int end = Math.min(i + batchSize, allDue.size());
                List<ScheduledNotification> batch = allDue.subList(i, end);
                int batchNumber = (i / batchSize) + 1;

                log.info("Processing batch {}/{} ({} notifications)", batchNumber, totalBatches, batch.size());
                dispatchService.dispatchBatch(batch, tenantId);
            }

            log.info("Scheduler completed. Processed {} notifications in {} batches",
                    allDue.size(), totalBatches);

        } catch (Exception e) {
            log.error("Scheduler failed for tenantId: {}, today: {}", tenantId, today, e);
        }
    }
}
