package org.egov.fileProcessor.consumer;


import org.egov.fileProcessor.planProcessor.microplanProcessor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaConsumer {
    @KafkaListener(topics = "plan-create" , groupId = "group_id")
    public void consume(String message)
    {
        System.out.println(message);
        //TODO check if the Message is a GUID
        microplanProcessor microplanProcessor = new microplanProcessor(message);
        microplanProcessor.GetInfo();

    }
}
