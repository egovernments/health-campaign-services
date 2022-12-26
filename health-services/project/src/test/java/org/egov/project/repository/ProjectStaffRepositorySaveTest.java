package org.egov.project.repository;

import org.egov.common.producer.Producer;
import org.egov.project.helper.ProjectStaffTestBuilder;
import org.egov.project.repository.rowmapper.ProjectStaffRowMapper;
import org.egov.project.web.models.ProjectStaff;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class ProjectStaffRepositorySaveTest {

    @InjectMocks
    private ProjectStaffRepository projectStaffRepository;

    @Mock
    private Producer producer;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ProjectStaffRowMapper projectStaffRowMapper;

    @Mock
    private HashOperations hashOperations;

    private List<ProjectStaff> projectStaffs;

    private static final String TOPIC = "save-project-staff-topic";

    @BeforeEach
    void setUp() {
        projectStaffs = Collections.singletonList(ProjectStaffTestBuilder
                .builder().withId().build());
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        ReflectionTestUtils.setField(projectStaffRepository, "timeToLive", "60");
    }

    @Test
    @DisplayName("should save and return saved objects back")
    void shouldSaveAndReturnSavedObjectsBack() {
        List<ProjectStaff> result = projectStaffRepository
                .save(projectStaffs, TOPIC);

        assertEquals(result, projectStaffs);
        verify(producer, times(1)).push(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("should save and add objects in the cache")
    void shouldSaveAndAddObjectsInTheCache() {
        projectStaffRepository.save(projectStaffs, TOPIC);

        InOrder inOrder = inOrder(producer, hashOperations);

        inOrder.verify(producer, times(1)).push(any(String.class), any(Object.class));
        inOrder.verify(hashOperations, times(1))
                .putAll(any(String.class), any(Map.class));
    }
}