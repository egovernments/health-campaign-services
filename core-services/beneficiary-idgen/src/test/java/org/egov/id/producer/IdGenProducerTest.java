package org.egov.id.producer;

import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.tracer.kafka.CustomKafkaTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IdGenProducerTest {

    @Mock
    private CustomKafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void shouldPrefixTopicWithSchemaWhenCentralInstance() {
        IdGenProducer idGenProducer = new IdGenProducer();
        ReflectionTestUtils.setField(idGenProducer, "kafkaTemplate", kafkaTemplate);
        ReflectionTestUtils.setField(idGenProducer, "multiStateInstanceUtil", new MultiStateInstanceUtil(1, true, 0));

        Object payload = new Object();
        idGenProducer.push("bednet.city.a", "save-in-id-pool", payload);

        verify(kafkaTemplate).send("bednet-save-in-id-pool", payload);
    }

    @Test
    void shouldNotPrefixTopicWhenSingleInstance() {
        IdGenProducer idGenProducer = new IdGenProducer();
        ReflectionTestUtils.setField(idGenProducer, "kafkaTemplate", kafkaTemplate);
        ReflectionTestUtils.setField(idGenProducer, "multiStateInstanceUtil", new MultiStateInstanceUtil(1, false, 0));

        Object payload = new Object();
        idGenProducer.push("bednet.city.a", "save-in-id-pool", payload);

        verify(kafkaTemplate).send("save-in-id-pool", payload);
    }

    @Test
    void shouldSendToGivenTopicWithLegacyTwoArgPush() {
        IdGenProducer idGenProducer = new IdGenProducer();
        ReflectionTestUtils.setField(idGenProducer, "kafkaTemplate", kafkaTemplate);

        Object payload = new Object();
        idGenProducer.push("save-in-id-pool", payload);

        verify(kafkaTemplate).send("save-in-id-pool", payload);
    }

    @Test
    void shouldSendWithKeyToGivenTopic() {
        IdGenProducer idGenProducer = new IdGenProducer();
        ReflectionTestUtils.setField(idGenProducer, "kafkaTemplate", kafkaTemplate);

        Object payload = new Object();
        idGenProducer.pushWithKey("id-gen-consumer-bulk-create-topic", "bednet.city.a-50000", payload);

        verify(kafkaTemplate).send("id-gen-consumer-bulk-create-topic", "bednet.city.a-50000", payload);
    }

    @Test
    void shouldSendWithKeyToStatePrefixedTopicWhenCentralInstance() {
        IdGenProducer idGenProducer = new IdGenProducer();
        ReflectionTestUtils.setField(idGenProducer, "kafkaTemplate", kafkaTemplate);
        ReflectionTestUtils.setField(idGenProducer, "multiStateInstanceUtil", new MultiStateInstanceUtil(1, true, 0));

        Object payload = new Object();
        idGenProducer.pushWithKey("bednet.city.a", "save-in-id-pool", "B-26-000000000001", payload);

        verify(kafkaTemplate).send("bednet-save-in-id-pool", "B-26-000000000001", payload);
    }
}
