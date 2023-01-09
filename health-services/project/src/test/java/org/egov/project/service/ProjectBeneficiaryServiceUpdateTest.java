package org.egov.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.mdms.MdmsCriteriaReq;
import org.apache.commons.io.IOUtils;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.service.MdmsService;
import org.egov.project.helper.BeneficiaryRequestTestBuilder;
import org.egov.project.helper.ProjectBeneficiaryTestBuilder;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.egov.project.web.models.ApiOperation;
import org.egov.project.web.models.BeneficiaryRequest;
import org.egov.project.web.models.Household;
import org.egov.project.web.models.HouseholdResponse;
import org.egov.project.web.models.HouseholdSearchRequest;
import org.egov.project.web.models.Individual;
import org.egov.project.web.models.IndividualResponse;
import org.egov.project.web.models.IndividualSearchRequest;
import org.egov.project.web.models.Project;
import org.egov.project.web.models.ProjectBeneficiary;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectBeneficiaryServiceUpdateTest {

    private final String HOUSEHOLD_RESPONSE_FILE_NAME = "/responses/mdms-household-response.json";

    private final String INDIVIDUAL_RESPONSE_FILE_NAME = "/responses/mdms-individual-response.json";


    @InjectMocks
    private ProjectBeneficiaryService projectBeneficiaryService;

    @Mock
    private ProjectService projectService;

    @Mock
    private ProjectBeneficiaryRepository projectBeneficiaryRepository;

    @Mock
    private MdmsService mdmsService;

    @Mock
    private ServiceRequestClient serviceRequestClient;

    private BeneficiaryRequest request;

    private List<String> projectBeneficiaryIds;

    private void mockServiceRequestClient() throws Exception {
        when(serviceRequestClient.fetchResult(any(StringBuilder.class), any(), eq(HouseholdResponse.class))).thenReturn(
                HouseholdResponse.builder().
                        household(
                                Collections.singletonList(
                                        Household.builder().build()
                                )
                        ).
                        build()
        );
    }


    private void mockProjectFindIds() {
        when(projectService.findByIds(any(List.class))).thenReturn(Collections.singletonList(
                Project.builder().id("some-project-id").projectTypeId("some-project-type-id").build()));
    }
    @BeforeEach
    void setUp() throws Exception {
        request = BeneficiaryRequestTestBuilder.builder()
                .withOneProjectBeneficiary()
                .build();
        request.setApiOperation(ApiOperation.UPDATE);
        projectBeneficiaryIds = request.getProjectBeneficiary().stream().map(ProjectBeneficiary::getId)
                .collect(Collectors.toList());
    }


    private void mockValidateProjectId() {
        lenient().when(projectService.validateProjectIds(any(List.class)))
                .thenReturn(Collections.singletonList("some-project-id"));
    }
    private void mockValidateBeneficiarytId() {
        lenient().when(projectBeneficiaryRepository.validateIds(any(List.class),eq("beneficiaryClientReferenceId")))
                .thenReturn(Collections.singletonList("beneficiaryClientReferenceId"));
    }

    private void mockFindById() {
        lenient().when(projectBeneficiaryRepository.findById(
                eq(projectBeneficiaryIds),
                anyString(),
                eq(false))
        ).thenReturn(request.getProjectBeneficiary());
    }

    private void mockMdms(String responseFileName) throws Exception {
        InputStream inputStream = getClass().getResourceAsStream(responseFileName);
        String responseString = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

        ObjectMapper map = new ObjectMapper();
        JsonNode node = map.readTree(responseString);

        when(mdmsService.fetchResultFromMdms(any(MdmsCriteriaReq.class), eq(JsonNode.class)))
                .thenReturn(node);
    }

    @Test
    @DisplayName("should update the lastModifiedTime in the result")
    void shouldUpdateTheLastModifiedTimeInTheResult() throws Exception {
        Long time = request.getProjectBeneficiary().get(0).getAuditDetails().getLastModifiedTime();
        mockValidateProjectId();
        mockValidateBeneficiarytId();
        mockFindById();
        mockServiceRequestClient();
        mockMdms(HOUSEHOLD_RESPONSE_FILE_NAME);
        mockProjectFindIds();

        List<ProjectBeneficiary> result = projectBeneficiaryService.update(request);

        assertNotEquals(time, result.get(0).getAuditDetails().getLastModifiedTime());
    }

    @Test
    @DisplayName("should update the row version in the result")
    void shouldUpdateTheRowVersionInTheResult() throws Exception {
        Integer rowVersion = request.getProjectBeneficiary().get(0).getRowVersion();
        mockValidateProjectId();
        mockValidateBeneficiarytId();
        mockFindById();
        mockServiceRequestClient();
        mockMdms(HOUSEHOLD_RESPONSE_FILE_NAME);
        mockProjectFindIds();

        List<ProjectBeneficiary> result = projectBeneficiaryService.update(request);

        assertEquals(rowVersion, result.get(0).getRowVersion() - 1);
    }

    @Test
    @DisplayName("should check if the request has valid project ids")
    void shouldCheckIfTheRequestHasValidProjecIds() throws Exception {
        mockValidateProjectId();
        mockValidateBeneficiarytId();
        mockFindById();
        mockServiceRequestClient();
        mockMdms(HOUSEHOLD_RESPONSE_FILE_NAME);
        mockProjectFindIds();
        projectBeneficiaryService.update(request);

        verify(projectService, times(1)).validateProjectIds(any(List.class));
    }

    @Test
    @DisplayName("should throw exception for any invalid project id")
    void shouldThrowExceptionForAnyInvalidProjectId() throws Exception {
        when(projectService.validateProjectIds(any(List.class))).thenReturn(Collections.emptyList());

        assertThrows(CustomException.class, () -> projectBeneficiaryService.update(request));
    }

    @Test
    @DisplayName("should fetch existing records using id")
    void shouldFetchExistingRecordsUsingId() throws Exception {
        mockValidateProjectId();
        mockValidateBeneficiarytId();
        mockFindById();
        mockServiceRequestClient();
        mockMdms(HOUSEHOLD_RESPONSE_FILE_NAME);
        mockProjectFindIds();

        projectBeneficiaryService.update(request);

        verify(projectBeneficiaryRepository, times(1)).findById(anyList(), anyString(), eq(false));
    }

    @Test
    @DisplayName("should throw exception if fetched records count doesn't match the count in request")
    void shouldThrowExceptionIfFetchedRecordsCountDoesntMatchTheCountInRequest() throws Exception {
        mockValidateProjectId();
        mockValidateBeneficiarytId();
        mockFindById();
        mockServiceRequestClient();
        mockMdms(HOUSEHOLD_RESPONSE_FILE_NAME);
        mockProjectFindIds();
        when(projectBeneficiaryRepository.findById(anyList(), anyString(), eq(false))).thenReturn(Collections.emptyList());

        assertThrows(CustomException.class, () -> projectBeneficiaryService.update(request));
    }

    @Test
    @DisplayName("should enrich ids from existing entities")
    void shouldEnrichIdsFromExistingEntities() throws Exception {
        mockValidateProjectId();
        mockValidateBeneficiarytId();
        mockFindById();
        mockServiceRequestClient();
        mockMdms(HOUSEHOLD_RESPONSE_FILE_NAME);
        mockProjectFindIds();

        BeneficiaryRequest requestWithoutClientReferenceId = BeneficiaryRequestTestBuilder.builder()
                .withOneProjectBeneficiary()
                .build();
        requestWithoutClientReferenceId.setApiOperation(ApiOperation.UPDATE);
        requestWithoutClientReferenceId.getProjectBeneficiary().get(0).setClientReferenceId(null);

        List<ProjectBeneficiary> projectBeneficiaries = projectBeneficiaryService.update(requestWithoutClientReferenceId);
        assertEquals(request.getProjectBeneficiary().get(0).getClientReferenceId(),
                projectBeneficiaries.get(0).getClientReferenceId());
    }

    @Test
    @DisplayName("should send the updates to kafka")
    void shouldSendTheUpdatesToKafka() throws Exception {
        mockValidateProjectId();
        mockValidateBeneficiarytId();
        mockFindById();
        mockServiceRequestClient();
        mockMdms(HOUSEHOLD_RESPONSE_FILE_NAME);
        mockProjectFindIds();
        when(projectBeneficiaryRepository.save(anyList(), anyString())).thenReturn(request.getProjectBeneficiary());

        List<ProjectBeneficiary> projectBeneficiaries = projectBeneficiaryService.update(request);

        assertEquals(request.getProjectBeneficiary(), projectBeneficiaries);
    }

    @Test
    @DisplayName("Should throw exception for row versions mismatch")
    void shouldThrowExceptionIfRowVersionIsNotSimilar() throws Exception {
        ProjectBeneficiary projectBeneficiary = ProjectBeneficiaryTestBuilder.builder().withId().build();
        projectBeneficiary.setRowVersion(123);
        BeneficiaryRequest beneficiaryRequest = BeneficiaryRequestTestBuilder.builder().withOneProjectBeneficiaryHavingId().build();
        mockValidateProjectId();
        mockValidateBeneficiarytId();
        mockFindById();
        mockServiceRequestClient();
        mockMdms(HOUSEHOLD_RESPONSE_FILE_NAME);
        mockProjectFindIds();

        assertThrows(Exception.class, () -> projectBeneficiaryService.update(beneficiaryRequest));
    }


    @Test
    @DisplayName("should call mdms client and service client for household beneficiary type")
    void shouldCallMdmsClientAndServiceClientWithHouseholdBeneficiaryType() throws Exception {
        mockValidateProjectId();
        mockValidateBeneficiarytId();
        mockFindById();
        mockMdms(HOUSEHOLD_RESPONSE_FILE_NAME);
        mockProjectFindIds();
        when(serviceRequestClient.fetchResult(
                any(StringBuilder.class),
                any(HouseholdSearchRequest.class),
                eq(HouseholdResponse.class))
        ).thenReturn(
                HouseholdResponse.builder().household(Collections.singletonList(Household.builder().build())).build()
        );

        projectBeneficiaryService.update(request);

        verify(projectService, times(1)).validateProjectIds(any(List.class));
        verify(mdmsService, times(1)).fetchResultFromMdms(any(), any());
        verify(serviceRequestClient, times(1)).fetchResult(
                any(StringBuilder.class),
                any(HouseholdSearchRequest.class),
                eq(HouseholdResponse.class)
        );
    }

    @Test
    @DisplayName("should throw exception on zero search results for household")
    void shouldThrowExceptionOnZeroHouseholdSearchResult() throws Exception {
        mockValidateProjectId();
        mockValidateBeneficiarytId();
        mockMdms(HOUSEHOLD_RESPONSE_FILE_NAME);
        mockProjectFindIds();
        when(serviceRequestClient.fetchResult(
                any(StringBuilder.class),
                any(),
                eq(HouseholdResponse.class))
        ).thenReturn(
                HouseholdResponse.builder().household(Collections.emptyList()).build()
        );

        assertThrows(CustomException.class, () -> projectBeneficiaryService.update(request));
    }

    @Test
    @DisplayName("should call mdms client and service client for individual beneficiary type")
    void shouldCallMdmsClientAndServiceClientWithIndividualBeneficiaryType() throws Exception {
        mockValidateProjectId();
        mockValidateBeneficiarytId();
        mockFindById();
        mockMdms(INDIVIDUAL_RESPONSE_FILE_NAME);
        mockProjectFindIds();
        when(serviceRequestClient.fetchResult(
                any(StringBuilder.class),
                any(IndividualSearchRequest.class),
                eq(IndividualResponse.class))
        ).thenReturn(
                IndividualResponse.builder().individual(Collections.singletonList(Individual.builder().build())).build()
        );

        projectBeneficiaryService.update(request);

        verify(projectService, times(1)).validateProjectIds(any(List.class));
        verify(mdmsService, times(1)).fetchResultFromMdms(any(), any());
        verify(serviceRequestClient, times(1)).fetchResult(
                any(StringBuilder.class),
                any(IndividualSearchRequest.class),
                eq(IndividualResponse.class)
        );
    }

    @Test
    @DisplayName("should throw exception on zero search results for individual")
    void shouldThrowExceptionOnZeroIndividualSearchResult() throws Exception {
        mockValidateProjectId();
        mockValidateBeneficiarytId();
        mockMdms(INDIVIDUAL_RESPONSE_FILE_NAME);
        mockProjectFindIds();
        when(serviceRequestClient.fetchResult(
                any(StringBuilder.class),
                any(),
                eq(IndividualResponse.class))
        ).thenReturn(
                IndividualResponse.builder().individual(Collections.emptyList()).build()
        );

        assertThrows(CustomException.class, () -> projectBeneficiaryService.update(request));
    }
}
