package org.egov.project.service;

import org.egov.common.models.project.ProjectFacility;
import org.egov.common.models.project.ProjectFacilityBulkRequest;
import org.egov.common.models.project.ProjectFacilityRequest;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.helper.ProjectFacilityBulkRequestTestBuilder;
import org.egov.project.helper.ProjectFacilityRequestTestBuilder;
import org.egov.project.repository.ProjectFacilityRepository;
import org.egov.project.service.enrichment.ProjectFacilityEnrichmentService;
import org.egov.project.validator.facility.PfFacilityIdValidator;
import org.egov.project.validator.facility.PfIsDeletedValidator;
import org.egov.project.validator.facility.PfNonExistentEntityValidator;
import org.egov.project.validator.facility.PfNullIdValidator;
import org.egov.project.validator.facility.PfProjectIdValidator;
import org.egov.project.validator.facility.PfRowVersionValidator;
import org.egov.project.validator.facility.PfUniqueEntityValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProjectFacilityServiceTest {

    @Spy
    @InjectMocks
    private ProjectFacilityService facilityService;

    @Mock
    private ProjectFacilityRepository repository;

    @Mock
    private PfIsDeletedValidator pfIsDeletedValidator;

    @Mock
    private PfNonExistentEntityValidator pfNonExistentEntityValidator;

    @Mock
    private PfNullIdValidator pfNullIdValidator;

    @Mock
    private PfRowVersionValidator pfRowVersionValidator;

    @Mock
    private PfUniqueEntityValidator pfUniqueEntityValidator;

    @Mock
    private PfProjectIdValidator pfProjectIdValidator;

    @Mock
    private PfFacilityIdValidator pfFacilityIdValidator;

    @Mock
    private ProjectFacilityEnrichmentService enrichmentService;

    @Mock
    private ProjectConfiguration configuration;

    List<Validator<ProjectFacilityBulkRequest, ProjectFacility>> validators;

    @BeforeEach
    void setUp() {
        validators = Arrays.asList(pfIsDeletedValidator, pfNonExistentEntityValidator, pfNullIdValidator,
                pfRowVersionValidator, pfUniqueEntityValidator, pfProjectIdValidator, pfFacilityIdValidator);
        ReflectionTestUtils.setField(facilityService, "validators", validators);

        lenient().when(configuration.getCreateProjectFacilityTopic()).thenReturn("create-facility-topic");
        lenient().when(configuration.getUpdateProjectFacilityTopic()).thenReturn("update-facility-topic");
        lenient().when(configuration.getDeleteProjectFacilityTopic()).thenReturn("delete-facility-topic");
    }


    @Test
    @DisplayName("should call create with isBulk false")
    void shouldCallCreateWithIsBulkFalse() {
        ProjectFacilityRequest request = ProjectFacilityRequestTestBuilder.builder()
                .withOneProjectFacility().withRequestInfo().build();
        doReturn(Collections.singletonList(request.getProjectFacility()))
                .when(facilityService).create(any(ProjectFacilityBulkRequest.class), anyBoolean());

        facilityService.create(request);

        verify(facilityService, times(1)).create(any(ProjectFacilityBulkRequest.class),
                eq(false));
    }

    @Test
    @DisplayName("should call update with isBulk false")
    void shouldCallUpdateWithIsBulkFalse() {
        ProjectFacilityRequest request = ProjectFacilityRequestTestBuilder.builder()
                .withOneProjectFacility().withRequestInfo().build();
        doReturn(Collections.singletonList(request.getProjectFacility()))
                .when(facilityService).update(any(ProjectFacilityBulkRequest.class), anyBoolean());

        facilityService.update(request);

        verify(facilityService, times(1)).update(any(ProjectFacilityBulkRequest.class),
                eq(false));
    }

    @Test
    @DisplayName("should call delete with isBulk false")
    void shouldCallDeleteWithIsBulkFalse() {
        ProjectFacilityRequest request = ProjectFacilityRequestTestBuilder.builder()
                .withOneProjectFacility().withRequestInfo().build();
        doReturn(Collections.singletonList(request.getProjectFacility()))
                .when(facilityService).delete(any(ProjectFacilityBulkRequest.class), anyBoolean());

        facilityService.delete(request);

        verify(facilityService, times(1)).delete(any(ProjectFacilityBulkRequest.class),
                eq(false));
    }

    @Test
    @DisplayName("should call kafka topic if valid facility found for create")
    void shouldCallKafkaTopicCreate() {
        ProjectFacilityBulkRequest facilityBulkRequest = ProjectFacilityBulkRequestTestBuilder.builder()
                .withProjectFacility().withRequestInfo().build();

        List<ProjectFacility> facility = facilityService.create(facilityBulkRequest, false);

        assertEquals(1, facility.size());
        verify(repository, times(1)).save(anyList(), eq("create-facility-topic"));
    }

    @Test
    @DisplayName("should not call kafka topic if no valid facility found for update")
    void shouldNotCallKafkaTopicUpdate() {
        ProjectFacilityBulkRequest facilityBulkRequest = ProjectFacilityBulkRequestTestBuilder.builder()
                .withProjectFacility().withRequestInfo().build();
        facilityBulkRequest.getProjectFacilities().get(0).setHasErrors(true);

        List<ProjectFacility> facility = facilityService.update(facilityBulkRequest, false);

        assertEquals(0, facility.size());
        verify(repository, times(0)).save(anyList(), anyString());
    }

    @Test
    @DisplayName("should call kafka topic if valid facility found for update")
    void shouldCallKafkaTopicUpdate() {
        ProjectFacilityBulkRequest facilityBulkRequest = ProjectFacilityBulkRequestTestBuilder.builder()
                .withProjectFacility().withRequestInfo().build();

        List<ProjectFacility> facility = facilityService.update(facilityBulkRequest, false);

        assertEquals(1, facility.size());
        verify(repository, times(1)).save(anyList(), eq("update-facility-topic"));
    }

    @Test
    @DisplayName("should not call kafka topic if no valid facility found for delete")
    void shouldNotCallKafkaTopicDelete() {
        ProjectFacilityBulkRequest facilityBulkRequest = ProjectFacilityBulkRequestTestBuilder.builder()
                .withProjectFacility().withRequestInfo().build();
        facilityBulkRequest.getProjectFacilities().get(0).setHasErrors(true);

        List<ProjectFacility> facility = facilityService.delete(facilityBulkRequest, false);

        assertEquals(0, facility.size());
        verify(repository, times(0)).save(anyList(), anyString());
    }

    @Test
    @DisplayName("should call kafka topic if valid facility found for delete")
    void shouldCallKafkaTopicDelete() {
        ProjectFacilityBulkRequest facilityBulkRequest = ProjectFacilityBulkRequestTestBuilder.builder()
                .withProjectFacility().withRequestInfo().build();

        List<ProjectFacility> facility = facilityService.delete(facilityBulkRequest, false);

        assertEquals(1, facility.size());
        verify(repository, times(1)).save(anyList(), eq("delete-facility-topic"));
    }
}
