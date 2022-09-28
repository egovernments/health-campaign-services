package org.egov.rn.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.digit.health.sync.kafka.Producer;
import org.egov.tracer.kafka.CustomKafkaTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ProducerTest {

    @Mock
    private CustomKafkaTemplate<String, Object> kafkaTemplate;

    private ObjectMapper objectMapper;

    @InjectMocks
    private Producer rnProducer;


    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        rnProducer = new Producer(kafkaTemplate, objectMapper);
    }

    @Test
    @DisplayName("should send message on the given kafka topic")
    void shouldSendMessageOnTheGivenKafkaTopic() {
        assertDoesNotThrow(() -> rnProducer.send("some_topic", "string"));
    }
}