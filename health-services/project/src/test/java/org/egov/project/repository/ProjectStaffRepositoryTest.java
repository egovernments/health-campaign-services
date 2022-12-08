package org.egov.project.repository;

import org.egov.common.producer.Producer;
import org.egov.project.web.models.ProjectStaff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProjectStaffRepositoryTest {

    @InjectMocks
    private ProjectStaffRepository projectStaffRepository;

    @Mock
    private Producer producer;

    @BeforeEach
    void setUp() {
        projectStaffRepository = new ProjectStaffRepository(producer);
    }

    @Test
    @DisplayName("should save project staff and return the same successfully")
    void shouldSaveProjectStaffAndReturnSameSuccessfully() {
        ProjectStaff expectedData = ProjectStaff.builder().build();

        ProjectStaff actualData = projectStaffRepository.save(expectedData);

        assertEquals(expectedData, actualData);
        verify(producer, times(1))
                .push(ProjectStaffRepository.SAVE_KAFKA_TOPIC, expectedData);
    }

}