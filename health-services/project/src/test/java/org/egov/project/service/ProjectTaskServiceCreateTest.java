package org.egov.project.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.service.IdGenService;
import org.egov.project.helper.TaskRequestTestBuilder;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.egov.project.repository.ProjectRepository;
import org.egov.project.repository.ProjectTaskRepository;
import org.egov.project.web.models.ProductVariant;
import org.egov.project.web.models.ProductVariantResponse;
import org.egov.project.web.models.ProductVariantSearchRequest;
import org.egov.project.web.models.Task;
import org.egov.project.web.models.TaskRequest;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectTaskServiceCreateTest {

    @InjectMocks
    private ProjectTaskService projectTaskService;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectBeneficiaryRepository projectBeneficiaryRepository;

    @Mock
    private IdGenService idGenService;

    @Mock
    private ServiceRequestClient client;

    @Mock
    private ProjectTaskRepository projectTaskRepository;

    private TaskRequest request;
    @BeforeEach
    void setUp() throws Exception {
        request = TaskRequestTestBuilder.builder().withTask().
                withRequestInfo().withApiOperationUpdate().build();
        List<String> validIds = new ArrayList<>();
        validIds.add("some-id");
        when(projectRepository.validateIds(anyList(), anyString())).thenReturn(validIds);

        List<String> validBeneficiaryIds = new ArrayList<>();
        validBeneficiaryIds.add("some-id");
        lenient().when(projectBeneficiaryRepository.validateIds(anyList(), anyString()))
                .thenReturn(validBeneficiaryIds);

        List<String> idList = new ArrayList<>();
        idList.add("some-id");
        lenient().when(idGenService.getIdList(any(RequestInfo.class),
                        any(String.class),
                        eq("project.task.id"), eq(""), anyInt()))
                .thenReturn(idList);

        ProductVariantResponse response = ProductVariantResponse.builder()
                .productVariant(Arrays.asList(
                        ProductVariant.builder().productId("some-id").id("some-id").build()))
                .responseInfo(ResponseInfo.builder().build()).build();
        lenient().when(client.fetchResult(any(StringBuilder.class), any(ProductVariantSearchRequest.class),
                eq(ProductVariantResponse.class))).thenReturn(response);


    }

    @Test
    @DisplayName("should set task with row version 1")
    void shouldTaskRowVersionSetToOne() throws Exception {
        List<Task> tasks = projectTaskService.create(request);

        assertEquals(1, tasks.stream().findAny().get().getRowVersion());
    }

    @Test
    @DisplayName("should set isDeleted to false for task")
    void shouldTaskIsDeletedFalse() throws Exception {
        List<Task> tasks = projectTaskService.create(request);

        assertFalse(tasks.stream().findAny().get().getIsDeleted());
    }

    @Test
    @DisplayName("should set AuditDetails for task")
    void shouldSetAuditDetailsForTask() throws Exception {
        List<Task> tasks = projectTaskService.create(request);

        assertNotNull(tasks.stream().findAny().get().getAuditDetails().getCreatedBy());
        assertNotNull(tasks.stream().findAny().get().getAuditDetails().getCreatedTime());
        assertNotNull(tasks.stream().findAny().get().getAuditDetails().getLastModifiedBy());
        assertNotNull(tasks.stream().findAny().get().getAuditDetails().getLastModifiedTime());
    }

    @Test
    @DisplayName("should throw exception if projectId does not exist")
    void shouldThrowExceptionIfProjectIdDoesNotExist() {
        when(projectRepository.validateIds(anyList(), anyString())).thenReturn(Collections.emptyList());

        assertThrows(CustomException.class, () -> projectTaskService.create(request));
        verify(projectRepository, times(1)).validateIds(anyList(), anyString());
    }

    @Test
    @DisplayName("should throw exception if projectBeneficiaryId does not exist")
    void shouldThrowExceptionIfProjectBeneficiaryIdDoesNotExist() {
        when(projectBeneficiaryRepository.validateIds(anyList(), anyString())).thenReturn(Collections.emptyList());

        assertThrows(CustomException.class, () -> projectTaskService.create(request));
        verify(projectBeneficiaryRepository, times(1)).validateIds(anyList(), anyString());
    }

    @Test
    @DisplayName("should set resource ID for task")
    void shouldSetResourceIDForTask() throws Exception {
       List<Task> tasks = projectTaskService.create(request);

       assertNotNull(tasks.stream().findAny().get().getResources().get(0).getId());
       assertNotNull(tasks.stream().findAny().get().getResources().get(1).getId());
    }

    @Test
    @DisplayName("should set audit details for task resources")
    void shouldSetAuditDetailsForTaskResources() throws Exception {
        List<Task> tasks = projectTaskService.create(request);

        Task task = tasks.stream().findAny().get();
        assertNotNull(task.getResources().stream().findAny().get().getAuditDetails().getCreatedBy());
        assertNotNull(task.getResources().stream().findAny().get().getAuditDetails().getCreatedTime());
        assertNotNull(task.getResources().stream().findAny().get().getAuditDetails().getLastModifiedBy());
        assertNotNull(task.getResources().stream().findAny().get().getAuditDetails().getLastModifiedTime());
    }

    @Test
    @DisplayName("should set row version and isDeleted for task resources")
    void shouldSetIsDeletedAndRowVersionsForTaskResources() throws Exception {
        List<Task> tasks = projectTaskService.create(request);

        Task task = tasks.stream().findAny().get();
        assertFalse(task.getResources().stream().findAny().get().getIsDeleted());
        assertEquals(task.getResources().stream().findAny().get().getRowVersion(), 1);
    }

    @Test
    @DisplayName("should set resources task id")
    void shouldSetTaskResourcesTaskId() throws Exception {
        List<Task> tasks = projectTaskService.create(request);

        Task task = tasks.stream().findAny().get();
        assertNotNull(task.getResources().stream().findAny().get().getTaskId());
    }

    @Test
    @DisplayName("should throw exception if product variant does not exist")
    void shouldThrowExceptionIfProductVaraintDoesNotExist() throws Exception {
        ProductVariantResponse response = ProductVariantResponse.builder()
                .productVariant(Collections.emptyList())
                .responseInfo(ResponseInfo.builder().build()).build();
        when(client.fetchResult(any(StringBuilder.class), any(ProductVariantSearchRequest.class),
                eq(ProductVariantResponse.class))).thenReturn(response);

        assertThrows(CustomException.class, () -> projectTaskService.create(request));
    }

    @Test
    @DisplayName("should send data to kafka")
    void shouldSendToKafkaForPersist() throws Exception {
        projectTaskService.create(request);

        verify(projectTaskRepository, times(1)).save(anyList(), eq("save-project-task-topic"));
    }
}
