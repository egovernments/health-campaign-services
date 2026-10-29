package org.egov.web.notification.push.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.egov.web.notification.push.config.PushProperties;
import org.egov.web.notification.push.repository.DeviceTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TokenCleanupJobTest {

    @InjectMocks
    private TokenCleanupJob job;

    @Mock
    private DeviceTokenRepository repository;

    @Mock
    private PushProperties properties;

    @Test
    void nonCentral_purgesDefaultSchemaWithRetentionCutoff() {
        ReflectionTestUtils.setField(job, "retentionDays", 30L);
        ReflectionTestUtils.setField(job, "schemas", "");
        when(properties.getIsCentralInstance()).thenReturn(false);
        long before = System.currentTimeMillis() - 30L * 24 * 3600 * 1000;

        job.purgeSoftDeletedTokens();

        verify(repository).purgeSoftDeleted(isNull(), longThat(c -> c >= before && c <= before + 5000));
    }

    @Test
    void central_purgesEachConfiguredSchema() {
        ReflectionTestUtils.setField(job, "schemas", " ba , ng ,");
        when(properties.getIsCentralInstance()).thenReturn(true);

        assertEquals(List.of("ba", "ng"), job.targetSchemas());
    }

    @Test
    void central_withoutSchemas_skips() {
        ReflectionTestUtils.setField(job, "schemas", "");
        when(properties.getIsCentralInstance()).thenReturn(true);

        job.purgeSoftDeletedTokens();

        verifyNoInteractions(repository);
    }
}
