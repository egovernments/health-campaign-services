package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.transformer.models.bill.BillReport;
import org.egov.transformer.models.bill.BillReportRequest;
import org.egov.transformer.transformationservice.BillReportTransformationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class BillReportConsumer {
    private final ObjectMapper objectMapper;
    private final BillReportTransformationService billReportTransformationService;

    @Autowired
    public BillReportConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper, BillReportTransformationService billReportTransformationService) {
        this.objectMapper = objectMapper;
        this.billReportTransformationService = billReportTransformationService;
    }

    @KafkaListener(topics = {"${transformer.consumer.save.bill.report.topic}",
            "${transformer.consumer.update.bill.report.topic}"})
    public void consumeAttendanceLog(ConsumerRecord<String, Object> payload,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            BillReportRequest billReportRequest = objectMapper.readValue((String) payload.value(), BillReportRequest.class);
            BillReport billReport = billReportRequest.getBillReport();
            billReportTransformationService.transform(billReport);
        } catch (Exception exception) {
            log.error("TRANSFORMER error in bill report consumer {}", ExceptionUtils.getStackTrace(exception));
        }
    }
}
