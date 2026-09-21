package org.egov.excelingestion.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Restarts Kafka listener containers that stopped while the application kept running
 * (e.g. the generation-init consumer thread dying on an unrecoverable rebalance +
 * downstream-timeout error). Without this the pod keeps passing health checks while
 * consuming nothing - a "zombie" listener - and init events pile up unprocessed.
 */
@Component
@Slf4j
public class KafkaListenerWatchdog {

    private final KafkaListenerEndpointRegistry registry;

    public KafkaListenerWatchdog(KafkaListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    @Scheduled(fixedDelayString = "${excel.ingestion.listener.watchdog.interval.ms:60000}",
               initialDelayString = "${excel.ingestion.listener.watchdog.interval.ms:60000}")
    public void restartDeadListeners() {
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            // Skip containers that are intentionally not auto-started (none today).
            if (container.isRunning() || !container.isAutoStartup()) {
                continue;
            }
            log.error("Kafka listener container '{}' is not running; restarting it", container.getListenerId());
            try {
                container.start();
                log.info("Kafka listener container '{}' restarted successfully", container.getListenerId());
            } catch (Exception e) {
                log.error("Failed to restart Kafka listener container '{}': {}",
                        container.getListenerId(), e.getMessage(), e);
            }
        }
    }
}
