package org.egov.id.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.models.idgen.IDPoolGenerationKafkaRequest;
import org.egov.common.models.idgen.IdRecord;
import org.egov.id.config.PropertiesManager;
import org.egov.id.producer.IdGenProducer;
import org.egov.id.repository.IdRepository;
import org.egov.id.service.EnrichmentService;
import org.egov.id.service.IdDispatchService;
import org.egov.id.service.IdGenerationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The Kafka consumer paths must not get the HTTP tenant access rule. The messages below would be
 * rejected on the HTTP endpoints (user without any role, tenant not covered by any role).
 */
@ExtendWith(MockitoExtension.class)
class IdGenerationConsumerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private IdRepository idRepository;

    @Mock
    private IdGenProducer idGenProducer;

    @Mock
    private PropertiesManager propertiesManager;

    @Mock
    private EnrichmentService enrichmentService;

    @Mock
    private IdGenerationService idGenerationService;

    private Map<String, Object> toMap(String json) throws Exception {
        return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
        });
    }

    @Test
    void bulkUpdateIsNotTenantChecked() throws Exception {
        IdDispatchService idDispatchService = new IdDispatchService(
                idRepository, idGenProducer, propertiesManager, Collections.emptyList(), enrichmentService);
        IdGenerationConsumer consumer = new IdGenerationConsumer(idDispatchService, idGenerationService, MAPPER);

        Map<String, Object> message = toMap("{\"RequestInfo\":{\"userInfo\":{\"uuid\":\"user-1\",\"roles\":[]}},"
                + "\"IdRecords\":[{\"id\":\"AB-1\",\"tenantId\":\"mz\",\"hasErrors\":false}]}");

        List<IdRecord> updated = consumer.bulkUpdate(message, "bulk-update-topic");

        assertEquals(1, updated.size());
        assertEquals("mz", updated.get(0).getTenantId());
        // one push for the id pool update and one for the transaction log
        verify(idGenProducer, times(2)).push(any(), any());
    }

    @Test
    void asyncIdPoolCreationIsNotTenantChecked() throws Exception {
        IdDispatchService idDispatchService = new IdDispatchService(
                idRepository, idGenProducer, propertiesManager, Collections.emptyList(), enrichmentService);
        IdGenerationConsumer consumer = new IdGenerationConsumer(idDispatchService, idGenerationService, MAPPER);

        Map<String, Object> message = toMap("{\"requestInfo\":{},\"tenantId\":\"mz\",\"chunkSize\":5}");

        consumer.consumeIDPoolCreationAsyncRequest(message);

        ArgumentCaptor<IDPoolGenerationKafkaRequest> captor = ArgumentCaptor.forClass(IDPoolGenerationKafkaRequest.class);
        verify(idGenerationService).handleAsyncIdPoolRequest(captor.capture());
        assertEquals("mz", captor.getValue().getTenantId());
        assertNull(captor.getValue().getRequestInfo().getUserInfo());
    }
}
