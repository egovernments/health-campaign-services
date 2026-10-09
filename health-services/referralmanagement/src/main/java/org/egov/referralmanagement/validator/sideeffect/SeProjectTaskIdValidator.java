package org.egov.referralmanagement.validator.sideeffect;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkResponse;
import org.egov.common.models.project.TaskSearch;
import org.egov.common.models.project.TaskSearchRequest;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 *  Validate whether project task exist in db or not using project task id and project task client beneficiary id for SideEffect object
 */
@Component
@Order(value = 3)
@Slf4j
public class SeProjectTaskIdValidator implements Validator<SideEffectBulkRequest, SideEffect> {
    private final ServiceRequestClient serviceRequestClient;
    private final ReferralManagementConfiguration referralManagementConfiguration;

    @Autowired
    public SeProjectTaskIdValidator(ServiceRequestClient serviceRequestClient, ReferralManagementConfiguration referralManagementConfiguration) {
        this.serviceRequestClient = serviceRequestClient;
        this.referralManagementConfiguration = referralManagementConfiguration;
    }


    /**
     * validating whether the project task id and client reference id exist or not in db
     * return the invalid Side effect objects as error map
     *
     * @param request of SideEffectBulkRequest
     * @return
     */
    @Override
    public Map<SideEffect, List<Error>> validate(SideEffectBulkRequest request) {
        log.info("validating project task id");
        Map<SideEffect, List<Error>> errorDetailsMap = new HashMap<>();
        List<SideEffect> entities = request.getSideEffects();
        Map<String, List<SideEffect>> tenantIdSideEffectMap = entities.stream().collect(Collectors.groupingBy(SideEffect::getTenantId));
        List<String> tenantIds = new ArrayList<>(tenantIdSideEffectMap.keySet());
        tenantIds.forEach(tenantId -> {
            List<SideEffect> sideEffectList = tenantIdSideEffectMap.get(tenantId);
            if (!sideEffectList.isEmpty()) {
                List<Task> existingTasks = null;
                final List<String> taskIdList = new ArrayList<>();
                final List<String> taskClientReferenceIdList = new ArrayList<>();
                sideEffectList.forEach(sideEffect -> {
                    addIgnoreNull(taskIdList, sideEffect.getTaskId());
                    addIgnoreNull(taskClientReferenceIdList, sideEffect.getTaskClientReferenceId());
                });
                TaskSearch taskSearch = TaskSearch.builder()
                        .id(taskIdList.isEmpty() ? null : taskIdList)
                        .clientReferenceId(taskClientReferenceIdList.isEmpty() ? null : taskClientReferenceIdList).build();
                try {
                    TaskBulkResponse taskBulkResponse = serviceRequestClient.fetchResult(
                            new StringBuilder(referralManagementConfiguration.getProjectHost()
                                    + referralManagementConfiguration.getProjectTaskSearchUrl()
                                    +"?limit=" + entities.size()
                                    + "&offset=0&tenantId=" + tenantId),
                            TaskSearchRequest.builder().requestInfo(request.getRequestInfo()).task(taskSearch).build(),
                            TaskBulkResponse.class
                    );
                    existingTasks = taskBulkResponse.getTasks();
                } catch (Exception e) {
                    throw new CustomException("Project Task failed to fetch", "Exception : "+e.getMessage());
                }
                final List<String> existingProjectTaskIds = existingTasks.stream().map(Task::getId).collect(Collectors.toList());
                final List<String> existingProjectReferenceTaskIds = existingTasks.stream().map(Task::getClientReferenceId).collect(Collectors.toList());
                List<SideEffect> invalidEntities = entities.stream().filter(notHavingErrors()).filter(entity ->
                        !existingProjectReferenceTaskIds.contains(entity.getTaskClientReferenceId())
                            && !existingProjectTaskIds.contains(entity.getTaskId())
                    ).collect(Collectors.toList());
                invalidEntities.forEach(sideEffect -> {
                    Error error = getErrorForNonExistentEntity();
                    populateErrorDetails(sideEffect, error, errorDetailsMap);
                });

            }
        });

        return errorDetailsMap;
    }

    private void addIgnoreNull(List<String> list, String item) {
        if(Objects.nonNull(item)) list.add(item);
    }
}
