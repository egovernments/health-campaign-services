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
import static org.egov.processor.config.ServiceConstants.FILE_TEMPLATE_IDENTIFIER_DRAFT_INPROGRESS;

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
     * Sets the filestore ID for the file with template identifier "FILE_TEMPLATE_IDENTIFIER_DRAFT_INPROGRESS".
     * If the file exists, the filestore ID is updated. If the file does not exist, a new file is added
     * with the specified filestore ID and input file type.
     *
     * @param planConfigurationRequest the plan configuration request containing the list of files
     * @param fileStoreId the filestore ID to set or add
     * @param inputFileType the input file type for the new file if added
     */
    public void setOrAddFileForDraft(PlanConfigurationRequest planConfigurationRequest, String fileStoreId,
                                                     File.InputFileTypeEnum inputFileType) {
        List<File> files = planConfigurationRequest.getPlanConfiguration().getFiles();

        // Check if file with the specified templateIdentifier exists
        File existingFile = files.stream()
                .filter(file -> FILE_TEMPLATE_IDENTIFIER_DRAFT_INPROGRESS.equals(file.getTemplateIdentifier()))
                .findFirst()
                .orElse(null);

        if (!ObjectUtils.isEmpty(existingFile)) {
            // If the file exists, update the filestoreId and change template identifier to Draft Complete
            existingFile.setFilestoreId(fileStoreId);
            existingFile.setTemplateIdentifier(FILE_TEMPLATE_IDENTIFIER_DRAFT_COMPLETE);
        } else {
            // If the file doesn't exist, add a new file
            File estimationFile = File.builder()
                    .filestoreId(fileStoreId)
                    .inputFileType(inputFileType)
                    .templateIdentifier(FILE_TEMPLATE_IDENTIFIER_DRAFT_COMPLETE)
                    .active(true)
                    .build();
            files.add(estimationFile);
        }

        // Set workflow to null to ensure plan configuration is processed as a new entity without workflow state
        planConfigurationRequest.getPlanConfiguration().setWorkflow(null);
    }


}
