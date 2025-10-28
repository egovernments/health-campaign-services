package org.egov.processor.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.processor.config.Configuration;
import org.egov.processor.repository.ServiceRequestRepository;
import org.egov.processor.web.models.*;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import static org.egov.processor.config.ErrorConstants.ERROR_WHILE_FETCHING_FROM_PLAN_SERVICE;
import static org.egov.processor.config.ServiceConstants.FILE_TEMPLATE_IDENTIFIER_DRAFT_COMPLETE;

@Component
@Slf4j
public class PlanConfigurationUtil {

    private ServiceRequestRepository serviceRequestRepository;

    private Configuration config;

    private ObjectMapper mapper;

    public PlanConfigurationUtil(ServiceRequestRepository serviceRequestRepository, Configuration config, ObjectMapper mapper) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.config = config;
        this.mapper = mapper;
    }

    /**
     * Searches for plan configurations based on the given search request.
     *
     * @param planConfigurationSearchRequest The request object containing search criteria.
     * @return A list of planConfiguration objects that match the search criteria.
     *         If no matching configurations are found or an error occurs, an empty list is returned.
     */
    public List<PlanConfiguration> search(PlanConfigurationSearchRequest planConfigurationSearchRequest)
    {
        List<PlanConfiguration> planConfigurationList = new ArrayList<>();
        PlanConfigurationResponse planConfigurationResponse = null;
        Object response = new HashMap<>();

        StringBuilder uri = new StringBuilder();
        uri.append(config.getPlanConfigHost()).append(config.getPlanConfigEndPoint());

        try {
            response = serviceRequestRepository.fetchResult(uri, planConfigurationSearchRequest);
            planConfigurationResponse = mapper.convertValue(response, PlanConfigurationResponse.class);
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_FROM_PLAN_SERVICE, e);
        }


        if(planConfigurationResponse != null)
            return planConfigurationResponse.getPlanConfiguration();
        else
            return planConfigurationList;
    }

    public void orderPlanConfigurationOperations(PlanConfigurationRequest planConfigurationRequest) {
        planConfigurationRequest.getPlanConfiguration().getOperations().sort(Comparator.comparingInt(Operation::getExecutionOrder));
    }

    /**
     * Builds a PlanConfigurationSearchRequest using information from the DraftRequest,
     * including the plan configuration ID, tenant ID, and request info.
     *
     * @param draftRequest the DraftRequest containing search details
     * @return a PlanConfigurationSearchRequest with the search criteria and request info
     */
    public PlanConfigurationSearchRequest buildPlanConfigurationSearchRequest(DraftRequest draftRequest) {
        PlanConfigurationSearchCriteria searchCriteria = PlanConfigurationSearchCriteria.builder()
                .id(draftRequest.getDraftDetails().getPlanConfigurationId())
                .tenantId(draftRequest.getDraftDetails().getTenantId())
                .build();

        return PlanConfigurationSearchRequest.builder()
                .requestInfo(draftRequest.getRequestInfo())
                .planConfigurationSearchCriteria(searchCriteria)
                .build();
    }

    /**
     * Adds a new draft file to the list of files in the given PlanConfigurationRequest.
     * If a file with the template identifier - DraftComplete already exists,
     * it is marked as inactive before adding the new file. The newly added file is marked as active.
     *
     * @param planConfigurationRequest the request object containing the plan configuration to update
     * @param fileStoreId              the file store ID of the new draft file
     * @param inputFileType            the type of the input file being added
     */
    public void addNewFileForDraft(PlanConfigurationRequest planConfigurationRequest, String fileStoreId,
                                   File.InputFileTypeEnum inputFileType) {
        List<File> files = planConfigurationRequest.getPlanConfiguration().getFiles();

        // Check if file with the specified templateIdentifier exists
        File existingFile = files.stream()
                .filter(file -> FILE_TEMPLATE_IDENTIFIER_DRAFT_COMPLETE.equalsIgnoreCase(file.getTemplateIdentifier()))
                .findFirst()
                .orElse(null);

        if (!ObjectUtils.isEmpty(existingFile)) {
            // If the file exists, make it inactive
            existingFile.setActive(false);
        }

        // Add a new file
        File estimationFile = File.builder()
                .filestoreId(fileStoreId)
                .inputFileType(inputFileType)
                .templateIdentifier(FILE_TEMPLATE_IDENTIFIER_DRAFT_COMPLETE)
                .active(true)
                .build();
        files.add(estimationFile);

        // Set workflow to null to ensure plan configuration is processed as a new entity without workflow state
        planConfigurationRequest.getPlanConfiguration().setWorkflow(null);
    }


}
