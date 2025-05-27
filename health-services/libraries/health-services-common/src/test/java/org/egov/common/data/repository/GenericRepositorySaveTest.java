package org.egov.common.data.repository;

import org.egov.common.helpers.SomeObject;
import org.egov.common.helpers.SomeRepository;
import org.egov.common.producer.Producer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GenericRepositorySaveTest {
    @InjectMocks
    private SomeRepository someRepository;

    @Mock
    private Producer producer;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations hashOperations;

    private List<SomeObject> someObjects;

    private static final String TOPIC = "save-topic";

    @BeforeEach
    void setUp() {
        someObjects = new ArrayList<>();
        someObjects.add(SomeObject.builder()
                        .id("some-id")
                        .tenantId("some-tenant-id")
                        .otherField("other-field")
                        .isDeleted(false)
                .build());
        someObjects.add(SomeObject.builder()
                .id("other-id")
                        .tenantId("other-tenant-id")
                .isDeleted(true)
                .build());
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        ReflectionTestUtils.setField(someRepository, "timeToLive", "60");
    }

    @Test
    @DisplayName("should save and return saved objects back")
    void shouldSaveAndReturnSavedObjectsBack() {
        List<SomeObject> result = someRepository
                .save(someObjects, TOPIC);

        assertEquals(result, someObjects);
        verify(producer, times(1)).push(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("should save and add objects in the cache")
    @Disabled
    void shouldSaveAndAddObjectsInTheCache() {
        someRepository.save(someObjects, TOPIC);

        InOrder inOrder = inOrder(producer, hashOperations);

        inOrder.verify(producer, times(1)).push(any(String.class), any(String.class), any(Object.class));
        inOrder.verify(hashOperations, times(1))
                .putAll(any(String.class), any(Map.class));
    }
}