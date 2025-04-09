 package org.egov.id.consumer;

 import com.fasterxml.jackson.databind.ObjectMapper;
 import lombok.extern.slf4j.Slf4j;
 import org.egov.common.models.idgen.IdRecord;
 import org.egov.common.models.idgen.IdRecordBulkRequest;
 import org.egov.id.service.IdDispatchService;
 import org.egov.id.service.IdGenerationService;
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.beans.factory.annotation.Qualifier;
 import org.springframework.kafka.annotation.KafkaListener;
 import org.springframework.kafka.support.KafkaHeaders;
 import org.springframework.messaging.handler.annotation.Header;
 import org.springframework.stereotype.Component;

 import java.util.Collections;
 import java.util.List;
 import java.util.Map;

 @Component
 @Slf4j
 public class IdGenerationConsumer {

         private final IdDispatchService idDispatchService;

         private final ObjectMapper objectMapper;


         @Autowired
         public IdGenerationConsumer(IdDispatchService idDispatchService, IdGenerationService idGenerationService, @Qualifier("objectMapper") ObjectMapper objectMapper) {
             this.idDispatchService =  idDispatchService;
             this.objectMapper = objectMapper;
         }

//         @KafkaListener(topics = "${individual.consumer.bulk.create.topic}")
//         public List<Individual> bulkCreate(Map<String, Object> consumerRecord,
//                                            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
//             try {
//                 IndividualBulkRequest request = objectMapper.convertValue(consumerRecord, IndividualBulkRequest.class);
//                 return individualService.create(request, true);
//             } catch (Exception exception) {
//                 log.error("error in individual consumer bulk create", exception);
//                 return Collections.emptyList();
//             }
//         }

         @KafkaListener(topics = "${kafka.topics.consumer.bulk.update.topic}")
         public List<IdRecord> bulkUpdate(Map<String, Object> consumerRecord,
                                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
             try {
                 IdRecordBulkRequest request = objectMapper.convertValue(consumerRecord, IdRecordBulkRequest.class);
                 return idDispatchService.update(request, true);
             } catch (Exception exception) {
                 log.error("error in individual consumer bulk update", exception);
                 return Collections.emptyList();
             }
         }


 }

