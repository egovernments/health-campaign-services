package org.egov.adrm.validator.adverseevent;

import lombok.extern.slf4j.Slf4j;
import org.egov.adrm.config.AdrmConfiguration;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.project.BeneficiaryBulkRequest;
import org.egov.common.models.project.BeneficiaryBulkResponse;
import org.egov.common.models.project.BeneficiaryResponse;
import org.egov.common.models.project.BeneficiarySearchRequest;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.project.ProjectBeneficiarySearch;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkResponse;
import org.egov.common.models.project.TaskSearch;
import org.egov.common.models.project.TaskSearchRequest;
import org.egov.common.models.adrm.adverseevent.AdverseEvent;
import org.egov.common.models.adrm.adverseevent.AdverseEventBulkRequest;
import org.egov.common.validator.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;


@Component
@Order(value = 3)
@Slf4j
public class AdProjectTaskIdValidator implements Validator<AdverseEventBulkRequest, AdverseEvent> {
    private final ServiceRequestClient serviceRequestClient;
    private final AdrmConfiguration adrmConfiguration;

    @Autowired
    public AdProjectTaskIdValidator(ServiceRequestClient serviceRequestClient, AdrmConfiguration adrmConfiguration) {
        this.serviceRequestClient = serviceRequestClient;
        this.adrmConfiguration = adrmConfiguration;
    }


    @Override
    public Map<AdverseEvent, List<Error>> validate(AdverseEventBulkRequest request) {
        log.info("validating project task id");
        Map<AdverseEvent, List<Error>> errorDetailsMap = new HashMap<>();
        List<AdverseEvent> entities = request.getAdverseEvents();
        Map<String, List<AdverseEvent>> tenantIdAdverseEventMap = entities.stream().collect(Collectors.groupingBy(AdverseEvent::getTenantId));
        List<String> tenantIds = new ArrayList<>(tenantIdAdverseEventMap.keySet());
        tenantIds.forEach(tenantId -> {
            List<AdverseEvent> adverseEventList = tenantIdAdverseEventMap.get(tenantId);
            if (!adverseEventList.isEmpty()) {
                List<Task> existingTasks = null;
                List<ProjectBeneficiary> existingProjectBeneficiaries = null;
                final List<String> projectBeneficiaryIdList = new ArrayList<>();
                final List<String> projectBeneficiaryClientReferenceIdList = new ArrayList<>();
                final List<String> taskIdList = new ArrayList<>();
                final List<String> taskClientReferenceIdList = new ArrayList<>();
                try {
                    adverseEventList.forEach(adverseEvent -> {
                        addIgnoreNull(projectBeneficiaryIdList, adverseEvent.getProjectBeneficiaryId());
                        addIgnoreNull(projectBeneficiaryClientReferenceIdList, adverseEvent.getProjectBeneficiaryClientReferenceId());
                        addIgnoreNull(taskIdList, adverseEvent.getTaskId());
                        addIgnoreNull(taskClientReferenceIdList, adverseEvent.getTaskClientReferenceId());
                    });
                    TaskSearch taskSearch = TaskSearch.builder()
                            .id(taskIdList.isEmpty() ? null : taskIdList)
                            .clientReferenceId(taskClientReferenceIdList.isEmpty() ? null : taskClientReferenceIdList).build();
                    TaskBulkResponse taskBulkResponse = serviceRequestClient.fetchResult(
                            new StringBuilder(adrmConfiguration.getProjectHost()
                                    + adrmConfiguration.getProjectTaskSearchUrl()
                                    + "?offset=0&tenantId=" + tenantId),
                            TaskSearchRequest.builder().requestInfo(request.getRequestInfo()).task(taskSearch).build(),
                            TaskBulkResponse.class
                    );
                    existingTasks = taskBulkResponse.getTasks();
                    ProjectBeneficiarySearch projectBeneficiarySearch = ProjectBeneficiarySearch.builder()
                            .id(projectBeneficiaryIdList.isEmpty() ? null : projectBeneficiaryIdList)
                            .clientReferenceId(projectBeneficiaryClientReferenceIdList.isEmpty() ? null : projectBeneficiaryClientReferenceIdList)
                            .build();
                    BeneficiaryBulkResponse beneficiaryBulkResponse = serviceRequestClient.fetchResult(
                            new StringBuilder(adrmConfiguration.getProjectHost()
                                    + adrmConfiguration.getProjectBeneficiarySearchUrl()
                                    + "?offset=0&tenantId=" + tenantId),
                            BeneficiarySearchRequest.builder().requestInfo(request.getRequestInfo()).projectBeneficiary(projectBeneficiarySearch).build(),
                            BeneficiaryBulkResponse.class
                    );
                    existingProjectBeneficiaries = beneficiaryBulkResponse.getProjectBeneficiaries();
                } catch (QueryBuilderException e) {
                    if(existingTasks == null) existingTasks = Collections.emptyList();
                    existingProjectBeneficiaries = Collections.emptyList();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                final List<String> existingProjectBeneficiaryIds = new ArrayList<>();
                final List<String> existingProjectBeneficiaryClientReferenceIds = new ArrayList<>();
                existingProjectBeneficiaries.forEach(projectBeneficiary -> {
                    existingProjectBeneficiaryIds.add(projectBeneficiary.getId());
                    existingProjectBeneficiaryClientReferenceIds.add(projectBeneficiary.getClientReferenceId());
                });
                final List<String> existingProjectTaskIds = existingTasks.stream().map(Task::getId).collect(Collectors.toList());
                final List<String> existingProjectReferenceTaskIds = existingTasks.stream().map(Task::getClientReferenceId).collect(Collectors.toList());
                List<AdverseEvent> invalidEntities = entities.stream().filter(notHavingErrors()).filter(entity ->
                                !existingProjectTaskIds.contains(entity.getTaskId())
                                        && !existingProjectReferenceTaskIds.contains(entity.getTaskClientReferenceId())
                                        && !existingProjectBeneficiaryIds.contains(entity.getProjectBeneficiaryId())
                                        && !existingProjectBeneficiaryClientReferenceIds.contains(entity.getProjectBeneficiaryClientReferenceId())
                        ).collect(Collectors.toList());
                invalidEntities.forEach(adverseEvent -> {
                    Error error = getErrorForNonExistentEntity();
                    populateErrorDetails(adverseEvent, error, errorDetailsMap);
                });

            }
        });

        return errorDetailsMap;
    }

    private void addIgnoreNull(List<String> list, String item) {
        if(Objects.nonNull(item)) list.add(item);
    }
}
