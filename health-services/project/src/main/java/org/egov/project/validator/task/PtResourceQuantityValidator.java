package org.egov.project.validator.task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.mdms.MasterDetail;
import digit.models.coremodels.mdms.MdmsCriteria;
import digit.models.coremodels.mdms.MdmsCriteriaReq;
import digit.models.coremodels.mdms.ModuleDetail;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.Error;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.models.project.TaskQuantity;
import org.egov.common.models.project.TaskResource;
import org.egov.common.service.MdmsService;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.project.Constants.INTERNAL_SERVER_ERROR;
import static org.egov.project.Constants.MDMS_RESPONSE;
import static org.egov.project.Constants.TASK_QUANTITY;
import static org.egov.project.util.ProjectConstants.TASK_NOT_ALLOWED;

/**
 * The PtResourceQuantityValidator class is responsible for validating the resource quantity of tasks in a bulk request.
 * It checks whether the quantity adheres to the specified pattern defined in the project configuration.
 *
 * @author kanishq-egov
 */
@Component
@Order(value = 3)
@Slf4j
public class PtResourceQuantityValidator implements Validator<TaskBulkRequest, Task> {
    private final ProjectConfiguration projectConfiguration;

    private final MdmsService mdmsService;

    /**
     * Constructor for PtResourceQuantityValidator.
     *
     * @param projectConfiguration The configuration containing settings for the project module.
     */
    public PtResourceQuantityValidator(ProjectConfiguration projectConfiguration, MdmsService mdmsService) {
        this.projectConfiguration = projectConfiguration;
        this.mdmsService = mdmsService;
    }

    /**
     * Validates the resource quantity of tasks in a bulk request.
     *
     * @param request The TaskBulkRequest containing a list of tasks.
     * @return A map containing tasks with associated error details.
     */
    @Override
    public Map<Task, List<Error>> validate(TaskBulkRequest request) {
        // Map to store error details for each task
        Map<Task, List<Error>> errorDetailsMap = new HashMap<>();

        // Extract the list of tasks from the request
        List<Task> entities = request.getTasks();

        // Check if the list is not empty
        if(!entities.isEmpty()) {
            entities.forEach(task -> {
                List<Error> errors = new ArrayList<>();
                // Extract the list of task resources
                List<TaskResource> taskResources = task.getResources();
                for(TaskResource taskResource : taskResources){
                    Error error = validateResourceQuantity(taskResource, request.getRequestInfo());
                    if(error != null){
                        errors.add(error);
                    }
                }
                if (!errors.isEmpty()){
                    errorDetailsMap.put(task,errors);
                }
            });
        }
        return errorDetailsMap;
    }

    /**
     * Validates the resource quantity for a single task resource.
     *
     * @param taskResource The task resource to be validated.
     * @param requestInfo  The request information.
     * @return An Error object if validation fails, else null.
     */
    private Error validateResourceQuantity(TaskResource taskResource, RequestInfo requestInfo){
        String productVariantId = taskResource.getProductVariantId();
        String regex = getRegex(productVariantId, taskResource.getTenantId(), requestInfo);
        String errorMessage = getErrorMessage(productVariantId, taskResource.getTenantId(), requestInfo);
        if(regex == null){
            log.error("Failed to fetch regex for product variant id: {}",productVariantId);
            return Error.builder()
                    .errorMessage("Failed to fetch Regex")
                    .errorCode(TASK_NOT_ALLOWED)
                    .type(Error.ErrorType.NON_RECOVERABLE)
                    .exception(new CustomException(TASK_NOT_ALLOWED, "Failed to Fetch Regex Pattern"))
                    .build();
        }
        if(!CommonUtils.isValidPattern(Double.toString(taskResource.getQuantity()),regex)){
            String productVariantIdErrorMessage = (errorMessage+ " for product variant id: "+ productVariantId);
            return Error.builder()
                    .errorMessage(productVariantIdErrorMessage)
                    .errorCode(TASK_NOT_ALLOWED)
                    .type(Error.ErrorType.NON_RECOVERABLE)
                    .exception(new CustomException(TASK_NOT_ALLOWED,errorMessage))
                    .build();
        }
        return null;
    }

    /**
     * Retrieves the regex pattern from MDMS for the given product variant ID.
     *
     * @param productVariantId The ID of the product variant.
     * @param tenantId         The tenant ID.
     * @param requestInfo      The request information.
     * @return The regex pattern if found, otherwise null.
     */
    private String getRegex(String productVariantId,String tenantId, RequestInfo requestInfo){
        List<TaskQuantity> mdmsData = mdmsCall(tenantId, requestInfo);
        for(TaskQuantity data : mdmsData){
            if (data.getId().contains(productVariantId)){
                return data.getRegex();
            }else{
                log.error("Failed to fetch regex for product variant ID: {}", productVariantId);
            }
        }
        return null;
    }

    /**
     * Retrieves the error message from MDMS for the given product variant ID.
     *
     * @param productVariantId The ID of the product variant.
     * @param tenantId         The tenant ID.
     * @param requestInfo      The request information.
     * @return The error message if found, otherwise null.
     */
    private String getErrorMessage(String productVariantId,String tenantId, RequestInfo requestInfo){
        List<TaskQuantity> mdmsData = mdmsCall(tenantId, requestInfo);
        for(TaskQuantity data : mdmsData){
            if (data.getId().contains(productVariantId)){
                return data.getErrorMessage();
            }else{
                log.error("Failed to fetch error message for product variant ID: {}", productVariantId);
            }
        }
        return null;
    }

    /**
     * Makes a call to MDMS to fetch task quantity data.
     *
     * @param tenantId    The tenant ID.
     * @param requestInfo The request information.
     * @return A list of TaskQuantity objects.
     */
    private List<TaskQuantity> mdmsCall(String tenantId, RequestInfo requestInfo){
        JsonNode response = fetchMdmsResponse(requestInfo,tenantId, TASK_QUANTITY, projectConfiguration.getTaskMdmsModule());
        return convertToTaskQuantityList(response);
    }

    /**
     * Converts JSON response to a list of TaskQuantity objects.
     *
     * @param jsonNode The JSON node containing task quantity data.
     * @return A list of TaskQuantity objects.
     */
    private List<TaskQuantity> convertToTaskQuantityList(JsonNode jsonNode) {
        JsonNode taskTypesNode = jsonNode.get(projectConfiguration.getTaskMdmsModule()).withArray(TASK_QUANTITY);
        return new ObjectMapper().convertValue(taskTypesNode, new TypeReference<List<TaskQuantity>>() {
        });
    }

    /**
     * Makes a call to MDMS service to fetch MDMS configuration data.
     *
     * @param requestInfo The request information.
     * @param tenantId    The tenant ID.
     * @param name        The name of the MDMS configuration.
     * @param moduleName  The name of the MDMS module.
     * @return The MDMS response JSON node.
     */
    private JsonNode fetchMdmsResponse(RequestInfo requestInfo, String tenantId, String name, String moduleName){
        MdmsCriteriaReq serviceRegistry = getMdmsRequest(requestInfo, tenantId, name, moduleName);
        try{
            return mdmsService.fetchConfig(serviceRegistry, JsonNode.class).get(MDMS_RESPONSE);
        } catch(Exception e){
            throw new CustomException(INTERNAL_SERVER_ERROR, "Error while fetching mdms config");
        }
    }

    /**
     * Prepares MDMS request object.
     *
     * @param requestInfo The request information.
     * @param tenantId    The tenant ID.
     * @param masterName  The name of the master.
     * @param moduleName  The name of the module.
     * @return The MDMS criteria request object.
     */
    private MdmsCriteriaReq getMdmsRequest(RequestInfo requestInfo, String tenantId, String masterName, String moduleName){
        MasterDetail masterDetail = new MasterDetail();
        masterDetail.setName(masterName);
        List<MasterDetail> masterDetailsList = new ArrayList<>();
        masterDetailsList.add(masterDetail);
        ModuleDetail moduleDetail = new ModuleDetail();
        moduleDetail.setMasterDetails(masterDetailsList);
        moduleDetail.setModuleName(moduleName);
        List<ModuleDetail> moduleDetailList = new ArrayList<>();
        moduleDetailList.add(moduleDetail);
        MdmsCriteria mdmsCriteria = new MdmsCriteria();
        mdmsCriteria.setTenantId(tenantId.split("\\.")[0]);
        mdmsCriteria.setModuleDetails(moduleDetailList);
        MdmsCriteriaReq mdmsCriteriaReq = new MdmsCriteriaReq();
        mdmsCriteriaReq.setMdmsCriteria(mdmsCriteria);
        mdmsCriteriaReq.setRequestInfo(requestInfo);
        return mdmsCriteriaReq;
    }
}
