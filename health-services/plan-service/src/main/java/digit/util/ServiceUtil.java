package digit.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.repository.PlanConfigurationRepository;
import digit.web.models.*;
import org.egov.tracer.model.CustomException;
import org.postgresql.util.PGobject;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class ServiceUtil {

    private PlanConfigurationRepository planConfigurationRepository;

    private ObjectMapper objectMapper;

    public ServiceUtil(PlanConfigurationRepository planConfigurationRepository, ObjectMapper objectMapper) {
        this.planConfigurationRepository = planConfigurationRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Validates the given input string against the provided regex pattern.
     *
     * @param patternString the regex pattern to validate against
     * @param inputString   the input string to be validated
     * @return true if the input string matches the regex pattern, false otherwise
     */
    public Boolean validateStringAgainstRegex(String patternString, String inputString) {
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(inputString);
        return matcher.matches();
    }

    /**
     * Searches the plan config based on the plan config id provided
     *
     * @param planConfigId the plan config id to validate
     * @param tenantId     the tenant id of the plan config
     * @return list of planConfiguration for the provided plan config id
     */
    public List<PlanConfiguration> searchPlanConfigId(String planConfigId, String tenantId) {
        List<PlanConfiguration> planConfigurations = planConfigurationRepository.search(PlanConfigurationSearchCriteria.builder()
                .id(planConfigId)
                .tenantId(tenantId)
                .build());

        return planConfigurations;
    }

    /**
     * Converts the PlanEmployeeAssignmentRequest to a data transfer object (DTO)
     *
     * @param planEmployeeAssignmentRequest The request to be converted to DTO
     * @return a DTO for PlanEmployeeAssignmentRequest
     */
    public PlanEmployeeAssignmentRequestDTO convertToReqDTO(PlanEmployeeAssignmentRequest planEmployeeAssignmentRequest) {
        PlanEmployeeAssignment planEmployeeAssignment = planEmployeeAssignmentRequest.getPlanEmployeeAssignment();

        // Creating a new data transfer object (DTO) for PlanEmployeeAssignment
        PlanEmployeeAssignmentDTO planEmployeeAssignmentDTO = PlanEmployeeAssignmentDTO.builder()
                .id(planEmployeeAssignment.getId())
                .tenantId(planEmployeeAssignment.getTenantId())
                .planConfigurationId(planEmployeeAssignment.getPlanConfigurationId())
                .employeeId(planEmployeeAssignment.getEmployeeId())
                .role(planEmployeeAssignment.getRole())
                .jurisdiction(convertArrayToString(planEmployeeAssignment.getJurisdiction()))
                .additionalDetails(planEmployeeAssignment.getAdditionalDetails())
                .active(planEmployeeAssignment.getActive())
                .auditDetails(planEmployeeAssignment.getAuditDetails())
                .build();

        return PlanEmployeeAssignmentRequestDTO.builder()
                .requestInfo(planEmployeeAssignmentRequest.getRequestInfo())
                .planEmployeeAssignmentDTO(planEmployeeAssignmentDTO)
                .build();
    }

    /**
     * This is a helper function to convert an array of string to comma separated string
     *
     * @param stringList Array of string to be converted
     * @return a string
     */
    private String convertArrayToString(List<String> stringList) {
        return String.join(", ", stringList);
    }

    /**
     * This method is used to extract and parse JSON data into a JsonNode object
     *
     * @param pGobject postgreSQL specific object
     * @return returns a JsonNode
     */
    public JsonNode getAdditionalDetail(PGobject pGobject) {
        JsonNode additionalDetail = null;

        try {
            if (!ObjectUtils.isEmpty(pGobject)) {
                additionalDetail = objectMapper.readTree(pGobject.getValue());
            }
        } catch (IOException e) {
            throw new CustomException("PARSING_ERROR", "Failed to parse additionalDetails object");
        }
        return additionalDetail;
    }
}
