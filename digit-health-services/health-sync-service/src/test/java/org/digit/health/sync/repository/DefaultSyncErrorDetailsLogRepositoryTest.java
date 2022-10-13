package org.digit.health.sync.repository;

import org.digit.health.sync.kafka.Producer;
import org.digit.health.sync.repository.enums.RepositoryErrorCode;
import org.digit.health.sync.web.models.dao.SyncErrorDetailsLogData;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DefaultSyncErrorDetailsLogRepositoryTest {

    @Mock
    private Producer producer;

    @Test
    @DisplayName("should save sync error details log and return the same successfully")
    void shouldSaveSyncErrorDetailsLogDataAndReturnSameSuccessfully() {
        SyncErrorDetailsLogRepository repository = new DefaultSyncErrorDetailsLogRepository(producer);
        SyncErrorDetailsLogData expectedData = SyncErrorDetailsLogData.builder().build();

        SyncErrorDetailsLogData actualData = repository.save(expectedData);

        assertEquals(expectedData, actualData);
        verify(producer, times(1))
                .send(DefaultSyncErrorDetailsLogRepository.SAVE_KAFKA_TOPIC, expectedData);
    }

    @Test
    @DisplayName("should return null in case the payload is null")
    void shouldReturnNullInCaseThePayloadIsNull() {
        SyncErrorDetailsLogRepository repository = new DefaultSyncErrorDetailsLogRepository(producer);

        SyncErrorDetailsLogData actualData = repository.save(null);

        assertNull(actualData);
        verify(producer, times(0))
                .send(DefaultSyncErrorDetailsLogRepository.SAVE_KAFKA_TOPIC, null);
    }

    @Test
    @DisplayName("should throw custom exception with proper error code in case of any error")
    void shouldThrowCustomExceptionWithErrorCodeInCaseOfAnyError() {
        SyncErrorDetailsLogRepository repository = new DefaultSyncErrorDetailsLogRepository(producer);
        SyncErrorDetailsLogData expectedData = SyncErrorDetailsLogData.builder().build();
        doThrow(new RuntimeException("some_message")).when(producer)
                .send(DefaultSyncErrorDetailsLogRepository.SAVE_KAFKA_TOPIC, expectedData);
        CustomException ex = null;

        try {
            repository.save(expectedData);
        } catch (CustomException exception) {
            ex = exception;
        }

        assertNotNull(ex);
        assertEquals(RepositoryErrorCode.SAVE_ERROR.name(), ex.getCode());
        assertEquals(RepositoryErrorCode.SAVE_ERROR.message("some_message"),
                ex.getMessage());
    }
}
