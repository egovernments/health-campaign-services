package org.egov.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.mdms.MdmsCriteriaReq;
import org.apache.commons.io.IOUtils;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkResponse;
import org.egov.common.models.household.HouseholdSearchRequest;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkResponse;
import org.egov.common.models.individual.IndividualSearchRequest;
import org.egov.common.models.project.BeneficiaryBulkRequest;
import org.egov.common.models.project.BeneficiaryRequest;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.service.MdmsService;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.helper.BeneficiaryBulkRequestTestBuilder;
import org.egov.project.helper.BeneficiaryRequestTestBuilder;
import org.egov.project.helper.ProjectBeneficiaryTestBuilder;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.egov.project.service.enrichment.ProjectBeneficiaryEnrichmentService;
import org.egov.project.validator.beneficiary.BeneficiaryValidator;
import org.egov.project.validator.beneficiary.PbIsDeletedValidator;
import org.egov.project.validator.beneficiary.PbNonExistentEntityValidator;
import org.egov.project.validator.beneficiary.PbNullIdValidator;
import org.egov.project.validator.beneficiary.PbProjectIdValidator;
import org.egov.project.validator.beneficiary.PbRowVersionValidator;
import org.egov.project.validator.beneficiary.PbUniqueEntityValidator;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Mock
    private ProjectConfiguration projectConfiguration;

    @Mock
    private PbProjectIdValidator pbProjectIdValidator;

    @Mock
    private BeneficiaryValidator beneficiaryValidator;

    @Mock
    private PbNullIdValidator pbNullIdValidator;

    @Mock
    private PbNonExistentEntityValidator pbNonExistentEntityValidator;

    @Mock
    private PbUniqueEntityValidator pbUniqueEntityValidator;

    @Mock
    private PbIsDeletedValidator pbIsDeletedValidator;

    @Mock
    private PbRowVersionValidator pbRowVersionValidator;

    @Mock
    private ProjectBeneficiaryEnrichmentService projectBeneficiaryEnrichmentService;

    private List<Validator<BeneficiaryBulkRequest, ProjectBeneficiary>> validators;

    private BeneficiaryBulkRequest request;

    private List<String> projectBeneficiaryIds;

    private void mockServiceRequestClient() throws Exception {
        when(serviceRequestClient.fetchResult(any(StringBuilder.class), any(), eq(HouseholdBulkResponse.class))).thenReturn(
                HouseholdBulkResponse.builder().
                        households(
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
        request = BeneficiaryBulkRequestTestBuilder.builder()
                .withOneProjectBeneficiary()
                .build();
        projectBeneficiaryIds = request.getProjectBeneficiaries().stream().map(ProjectBeneficiary::getId)
                .collect(Collectors.toList());
        validators = Arrays.asList(pbNullIdValidator, pbNonExistentEntityValidator,
                pbUniqueEntityValidator, pbRowVersionValidator, pbIsDeletedValidator);
        ReflectionTestUtils.setField(projectBeneficiaryService, "validators", validators);
        lenient().when(projectConfiguration.getUpdateProjectBeneficiaryTopic()).thenReturn("update-topic");
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
                eq(false),
                anyString())
        ).thenReturn(request.getProjectBeneficiaries());
    }

    private void mockMdms(String responseFileName) throws Exception {
        InputStream inputStream = getClass().getResourceAsStream(responseFileName);
        String responseString = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

        ObjectMapper map = new ObjectMapper();
        JsonNode node = map.readTree(responseString);

        when(mdmsService.fetchConfig(any(MdmsCriteriaReq.class), eq(JsonNode.class)))
                .thenReturn(node);
    }

    @Test
    @DisplayName("should check if the request has valid project ids")
    @Disabled
    void shouldCheckIfTheRequestHasValidProjecIds() throws Exception {
        mockValidateProjectId();
        mockValidateBeneficiarytId();
        mockFindById();
        mockServiceRequestClient();
        mockMdms(HOUSEHOLD_RESPONSE_FILE_NAME);
        mockProjectFindIds();
        projectBeneficiaryService.update(request, false);

        verify(projectService, times(1)).validateProjectIds(any(List.class));
    }

    @Test
    @DisplayName("should throw exception for any invalid project id")
    @Disabled
    void shouldThrowExceptionForAnyInvalidProjectId() throws Exception {
        when(projectService.validateProjectIds(any(List.class))).thenReturn(Collections.emptyList());

        assertThrows(CustomException.class, () -> projectBeneficiaryService.update(request, false));
    }

    @Test
    @DisplayName("should throw exception if fetched records count doesn't match the count in request")
    @Disabled
    void shouldThrowExceptionIfFetchedRecordsCountDoesntMatchTheCountInRequest() throws Exception {
        mockValidateProjectId();
        mockValidateBeneficiarytId();
        mockFindById();
        mockServiceRequestClient();
        mockMdms(HOUSEHOLD_RESPONSE_FILE_NAME);
        mockProjectFindIds();
        when(projectBeneficiaryRepository.findById(anyList(), eq(false), anyString())).thenReturn(Collections.emptyList());

        assertThrows(CustomException.class, () -> projectBeneficiaryService.update(request, false));
    }

    @Test
    @DisplayName("should send the updates to kafka")
    void shouldSendTheUpdatesToKafka() throws Exception {
        when(projectBeneficiaryRepository.save(anyList(), anyString())).thenReturn(request.getProjectBeneficiaries());

        List<ProjectBeneficiary> projectBeneficiaries = projectBeneficiaryService.update(request, false);

        assertEquals(request.getProjectBeneficiaries(), projectBeneficiaries);
    }

    @Test
    @DisplayName("Should throw exception for row versions mismatch")
    @Disabled
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
    @Disabled
    void shouldCallMdmsClientAndServiceClientWithHouseholdBeneficiaryType() throws Exception {
        mockValidateProjectId();
        mockValidateBeneficiarytId();
        mockFindById();
        mockMdms(HOUSEHOLD_RESPONSE_FILE_NAME);
        mockProjectFindIds();
        when(serviceRequestClient.fetchResult(
                any(StringBuilder.class),
                any(HouseholdSearchRequest.class),
                eq(HouseholdBulkResponse.class))
        ).thenReturn(
                HouseholdBulkResponse.builder().households(Collections.singletonList(Household.builder().build())).build()
        );

        projectBeneficiaryService.update(request, false);

        verify(projectService, times(1)).validateProjectIds(any(List.class));
        verify(mdmsService, times(1)).fetchConfig(any(), any());
        verify(serviceRequestClient, times(1)).fetchResult(
                any(StringBuilder.class),
                any(HouseholdSearchRequest.class),
                eq(HouseholdBulkResponse.class)
        );
    }

    @Test
    @DisplayName("should throw exception on zero search results for household")
    @Disabled
    void shouldThrowExceptionOnZeroHouseholdSearchResult() throws Exception {
        mockValidateProjectId();
        mockValidateBeneficiarytId();
        mockMdms(HOUSEHOLD_RESPONSE_FILE_NAME);
        mockProjectFindIds();
        when(serviceRequestClient.fetchResult(
                any(StringBuilder.class),
                any(),
                eq(HouseholdBulkResponse.class))
        ).thenReturn(
                HouseholdBulkResponse.builder().households(Collections.emptyList()).build()
        );

        assertThrows(CustomException.class, () -> projectBeneficiaryService.update(request, false));
    }

    @Test
    @DisplayName("should call mdms client and service client for individual beneficiary type")
    @Disabled
    void shouldCallMdmsClientAndServiceClientWithIndividualBeneficiaryType() throws Exception {
        mockValidateProjectId();
        mockValidateBeneficiarytId();
        mockFindById();
        mockMdms(INDIVIDUAL_RESPONSE_FILE_NAME);
        mockProjectFindIds();
        when(serviceRequestClient.fetchResult(
                any(StringBuilder.class),
                any(IndividualSearchRequest.class),
                eq(IndividualBulkResponse.class))
        ).thenReturn(
                IndividualBulkResponse.builder().individual(Collections.singletonList(Individual.builder().build())).build()
        );

        projectBeneficiaryService.update(request, false);

        verify(projectService, times(1)).validateProjectIds(any(List.class));
        verify(mdmsService, times(1)).fetchConfig(any(), any());
        verify(serviceRequestClient, times(1)).fetchResult(
                any(StringBuilder.class),
                any(IndividualSearchRequest.class),
                eq(IndividualBulkResponse.class)
        );
    }

    @Test
    @DisplayName("should throw exception on zero search results for individual")
    @Disabled
    void shouldThrowExceptionOnZeroIndividualSearchResult() throws Exception {
        mockValidateProjectId();
        mockValidateBeneficiarytId();
        mockMdms(INDIVIDUAL_RESPONSE_FILE_NAME);
        mockProjectFindIds();
        when(serviceRequestClient.fetchResult(
                any(StringBuilder.class),
                any(),
                eq(IndividualBulkResponse.class))
        ).thenReturn(
                IndividualBulkResponse.builder().individual(Collections.emptyList()).build()
        );

        assertThrows(CustomException.class, () -> projectBeneficiaryService.update(request, false));
    }
}
