package org.egov.excelingestion.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for Kafka topics used in Excel Ingestion service
 */
@Configuration
@Getter
@Setter
public class KafkaTopicConfig {

    // Generation service topics
    @Value("${excel.ingestion.generation.save.topic}")
    private String generationSaveTopic;

    @Value("${excel.ingestion.generation.update.topic}")
    private String generationUpdateTopic;

    // Processing service topics
    @Value("${excel.ingestion.processing.save.topic}")
    private String processingSaveTopic;

    @Value("${excel.ingestion.processing.update.topic}")
    private String processingUpdateTopic;

    // Sheet data topics
    @Value("${excel.ingestion.sheet.data.save.topic}")
    private String sheetDataSaveTopic;

    @Value("${excel.ingestion.sheet.data.delete.topic}")
    private String sheetDataDeleteTopic;

    // Processing result topic
    @Value("${excel.ingestion.processing.result.topic}")
    private String processingResultTopic;
}