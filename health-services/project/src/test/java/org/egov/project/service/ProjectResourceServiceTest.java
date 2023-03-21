package org.egov.project.service;

import org.egov.common.models.project.ProjectResource;
import org.egov.common.models.project.ProjectResourceBulkRequest;
import org.egov.common.models.project.ProjectResourceRequest;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.helper.ProjectResourceBulkRequestTestBuilder;
import org.egov.project.helper.ProjectResourceRequestTestBuilder;
import org.egov.project.repository.ProjectResourceRepository;
import org.egov.project.service.enrichment.ProjectResourceEnrichmentService;
import org.egov.project.validator.resource.PrIsDeletedValidator;
import org.egov.project.validator.resource.PrNonExistentEntityValidator;
import org.egov.project.validator.resource.PrNullIdValidator;
import org.egov.project.validator.resource.PrProductVariantIdValidator;
import org.egov.project.validator.resource.PrProjectIdValidator;
import org.egov.project.validator.resource.PrRowVersionValidator;
import org.egov.project.validator.resource.PrUniqueEntityValidator;
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
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
class ProjectResourceServiceTest {

    @Spy
    @InjectMocks
    private ProjectResourceService service;

    @Mock
    private PrIsDeletedValidator prIsDeletedValidator;

    @Mock
    private PrNonExistentEntityValidator prNonExistentEntityValidator;

    @Mock
    private PrNullIdValidator prNullIdValidator;

    @Mock
    private PrProductVariantIdValidator prProductVariantIdValidator;

    @Mock
    private PrProjectIdValidator prProjectIdValidator;

    @Mock
    private PrRowVersionValidator prRowVersionValidator;

    @Mock
    private PrUniqueEntityValidator prUniqueEntityValidator;

    List<Validator<ProjectResourceBulkRequest, ProjectResource>> validators;

    @Mock
    ProjectConfiguration configuration;

    @Mock
    private ProjectResourceEnrichmentService enrichmentService;

    @Mock
    private ProjectResourceRepository projectResourceRepository;

    @BeforeEach
    void setUp() {
        validators = Arrays.asList(prIsDeletedValidator, prNonExistentEntityValidator, prNullIdValidator,
                prProjectIdValidator, prProductVariantIdValidator, prRowVersionValidator, prUniqueEntityValidator);
        ReflectionTestUtils.setField(service, "validators", validators);

        ReflectionTestUtils.setField(service, "isApplicableForCreate",
                (Predicate<Validator<ProjectResourceBulkRequest, ProjectResource>>) validator ->
                        validator.getClass().equals(PrProductVariantIdValidator.class));


        lenient().when(configuration.getCreateProjectResourceTopic()).thenReturn("create-project-resource-topic");
        lenient().when(configuration.getUpdateProjectResourceTopic()).thenReturn("update-project-resource-topic");
        lenient().when(configuration.getDeleteProjectResourceTopic()).thenReturn("delete-project-resource-topic");
    }

    @Test
    void shouldReturnProjectResourceResponse(){
        ProjectResourceRequest request = ProjectResourceRequestTestBuilder.builder().withProjectResource()
                .withRequestInfo().build();

        ProjectResource response = service.create(request);

        assertNotNull(response);
    }

    @Test
    @DisplayName("should call create with isBulk false")
    void shouldCallCreateWithIsBulkFalse() {
        ProjectResourceRequest request = ProjectResourceRequestTestBuilder.builder().withProjectResource()
                .withRequestInfo().build();
        doReturn(Collections.singletonList(request.getProjectResource()))
                .when(service).create(any(ProjectResourceBulkRequest.class), anyBoolean());

        service.create(request);

        verify(service, times(1)).create(any(ProjectResourceBulkRequest.class), eq(false));

    }
    @Test
    @DisplayName("should call update with isBulk false")
    void shouldCallUpdateWithIsBulkFalse() {
        ProjectResourceRequest request = ProjectResourceRequestTestBuilder.builder().withProjectResource()
                .withRequestInfo().build();
        doReturn(Collections.singletonList(request.getProjectResource()))
                .when(service).update(any(ProjectResourceBulkRequest.class), anyBoolean());

        service.update(request);

        verify(service, times(1)).update(any(ProjectResourceBulkRequest.class), eq(false));

    }

    @Test
    @DisplayName("should call delete with isBulk false")
    void shouldCallDeleteWithIsBulkFalse() {
        ProjectResourceRequest request = ProjectResourceRequestTestBuilder.builder().withProjectResource()
                .withRequestInfo().build();
        doReturn(Collections.singletonList(request.getProjectResource()))
                .when(service).delete(any(ProjectResourceBulkRequest.class), anyBoolean());

        service.delete(request);

        verify(service, times(1)).delete(any(ProjectResourceBulkRequest.class), eq(false));

    }

    @Test
    @DisplayName("should not call kafka topic if no valid project resource found for create")
    void shouldNotCallKafkaTopicCreate() {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequestTestBuilder.builder().withProjectResource()
                .withRequestInfo().build();
        request.getProjectResource().get(0).setHasErrors(true);

        List<ProjectResource> projectResources = service.create(request, false);

        assertEquals(0, projectResources.size());
        verify(projectResourceRepository, times(0)).save(anyList(), anyString());

    }

    @Test
    @DisplayName("should call kafka topic if valid project resource found for create")
    void shouldCallKafkaTopicCreate() {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequestTestBuilder.builder().withProjectResource()
                .withRequestInfo().build();

        List<ProjectResource> projectResources = service.create(request, false);

        assertEquals(1, projectResources.size());
        verify(projectResourceRepository, times(1)).save(anyList(), eq("create-project-resource-topic"));

    }

    @Test
    @DisplayName("should not call kafka topic if no valid project resource found for update")
    void shouldNotCallKafkaTopicUpdate() {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequestTestBuilder.builder().withProjectResource()
                .withRequestInfo().build();
        request.getProjectResource().get(0).setHasErrors(true);

        List<ProjectResource> projectResources = service.update(request, false);

        assertEquals(0, projectResources.size());
        verify(projectResourceRepository, times(0)).save(anyList(), anyString());
    }

    @Test
    @DisplayName("should call kafka topic if valid project resource found for update")
    void shouldCallKafkaTopicUpdate() {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequestTestBuilder.builder().withProjectResource()
                .withRequestInfo().build();

        List<ProjectResource> projectResources = service.update(request, false);

        assertEquals(1, projectResources.size());
        verify(projectResourceRepository, times(1)).save(anyList(), eq("update-project-resource-topic"));

    }

    @Test
    @DisplayName("should not call kafka topic if no valid project resource found for delete")
    void shouldNotCallKafkaTopicDelete() {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequestTestBuilder.builder().withProjectResource()
                .withRequestInfo().build();
        request.getProjectResource().get(0).setHasErrors(true);

        List<ProjectResource> projectResources = service.delete(request, false);

        assertEquals(0, projectResources.size());
        verify(projectResourceRepository, times(0)).save(anyList(), anyString());
    }

    @Test
    @DisplayName("should call kafka topic if valid project resource found for delete")
    void shouldCallKafkaTopicDelete() {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequestTestBuilder.builder().withProjectResource()
                .withRequestInfo().build();

        List<ProjectResource> projectResources = service.delete(request, false);

        assertEquals(1, projectResources.size());
        verify(projectResourceRepository, times(1)).save(anyList(), eq("delete-project-resource-topic"));

    }
}
