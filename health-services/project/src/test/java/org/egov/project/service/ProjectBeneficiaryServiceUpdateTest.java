package org.egov.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.UserSearchRequest;
import digit.models.coremodels.mdms.MdmsCriteriaReq;
import org.egov.common.contract.request.User;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.service.MdmsService;
import org.egov.project.helper.BeneficiaryRequestTestBuilder;
import org.egov.project.helper.ProjectBeneficiaryTestBuilder;
import org.egov.project.helper.ProjectStaffRequestTestBuilder;
import org.egov.project.helper.ProjectStaffTestBuilder;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.egov.project.web.models.ApiOperation;
import org.egov.project.web.models.BeneficiaryRequest;
import org.egov.project.web.models.Household;
import org.egov.project.web.models.HouseholdResponse;
import org.egov.project.web.models.Project;
import org.egov.project.web.models.ProjectBeneficiary;
import org.egov.project.web.models.ProjectStaff;
import org.egov.project.web.models.ProjectStaffRequest;
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

    private void mockMdms() throws Exception {
        String responseString = "{\n" +
                "    \"ResponseInfo\": null,\n" +
                "    \"MdmsRes\": {\n" +
                "        \"HCM-PROJECT-TYPES\": {\n" +
                "            \"projectTypes\": [\n" +
                "                {\n" +
                "                    \"id\": \"some-project-type-id\",\n" +
                "                    \"name\": \"Default project type configuration for LLIN Campaigns\",\n" +
                "                    \"code\": \"LLIN-Default\",\n" +
                "                    \"group\": \"MALARIA\",\n" +
                "                    \"beneficiaryType\": \"HOUSEHOLD\",\n" +
                "                    \"eligibilityCriteria\": [\n" +
                "                        \"All households are eligible.\",\n" +
                "                        \"Prison inmates are eligible.\"\n" +
                "                    ],\n" +
                "                    \"taskProcedure\": [\n" +
                "                        \"1 bednet is to be distributed per 2 household members.\",\n" +
                "                        \"If there are 4 household members, 2 bednets should be distributed.\",\n" +
                "                        \"If there are 5 household members, 3 bednets should be distributed.\"\n" +
                "                    ],\n" +
                "                    \"resources\": [\n" +
                "                        {\n" +
                "                            \"productVariantId\": \"943dd353-7641-4984-a59d-a01891cb05ca\",\n" +
                "                            \"isBaseUnitVariant\": false\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"productVariantId\": \"07bf993a-0d6a-4ff6-a1e6-58562e900d81\",\n" +
                "                            \"isBaseUnitVariant\": true\n" +
                "                        }\n" +
                "                    ]\n" +
                "                },\n" +
                "                {\n" +
                "                    \"id\": \"a6907f0c-7a91-4c76-afc2-a279d8a7b76a\",\n" +
                "                    \"name\": \"Mozambique specific configuration for LLIN Campaigns\",\n" +
                "                    \"code\": \"LLIN-Moz\",\n" +
                "                    \"group\": \"MALARIA\",\n" +
                "                    \"beneficiaryType\": \"HOUSEHOLD\",\n" +
                "                    \"eligibilityCriteria\": [\n" +
                "                        \"All households having members under the age of 18 are eligible.\",\n" +
                "                        \"Prison inmates are eligible.\"\n" +
                "                    ],\n" +
                "                    \"taskProcedure\": [\n" +
                "                        \"1 bednet is to be distributed per 2 household members.\",\n" +
                "                        \"If there are 4 household members, 2 bednets should be distributed.\",\n" +
                "                        \"If there are 5 household members, 3 bednets should be distributed.\"\n" +
                "                    ],\n" +
                "                    \"resources\": [\n" +
                "                        {\n" +
                "                            \"productVariantId\": \"ff940b7a-b990-4ab9-a699-13db261306ed\",\n" +
                "                            \"isBaseUnitVariant\": false\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"productVariantId\": \"07bf993a-0d6a-4ff6-a1e6-58562e900d81\",\n" +
                "                            \"isBaseUnitVariant\": true\n" +
                "                        }\n" +
                "                    ]\n" +
                "                }\n" +
                "            ]\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "\n" +
                "\n" +
                "\n" +
                "\n" +
                "\n" +
                "\n" +
                "\n" +
                "\n";
        ObjectMapper map = new ObjectMapper();
        JsonNode node = map.readTree(responseString);

        when(mdmsService.fetchResultFromMdms(any(MdmsCriteriaReq.class), eq(JsonNode.class)))
                .thenReturn(node);
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


    private void mockFindById() {
        lenient().when(projectBeneficiaryRepository.findById(projectBeneficiaryIds)).thenReturn(request.getProjectBeneficiary());
    }


    @Test
    @DisplayName("should update the lastModifiedTime in the result")
    void shouldUpdateTheLastModifiedTimeInTheResult() throws Exception {
        Long time = request.getProjectBeneficiary().get(0).getAuditDetails().getLastModifiedTime();

        mockValidateProjectId();
        mockFindById();
        mockServiceRequestClient();
        mockMdms();
        mockProjectFindIds();

        List<ProjectBeneficiary> result = projectBeneficiaryService.update(request);

        assertNotEquals(time, result.get(0).getAuditDetails().getLastModifiedTime());
    }

    @Test
    @DisplayName("should update the row version in the result")
    void shouldUpdateTheRowVersionInTheResult() throws Exception {
        Integer rowVersion = request.getProjectBeneficiary().get(0).getRowVersion();

        mockValidateProjectId();
        mockFindById();
        mockServiceRequestClient();
        mockMdms();
        mockProjectFindIds();

        List<ProjectBeneficiary> result = projectBeneficiaryService.update(request);

        assertEquals(rowVersion, result.get(0).getRowVersion() - 1);
    }

    @Test
    @DisplayName("should check if the request has valid project ids")
    void shouldCheckIfTheRequestHasValidProductIds() throws Exception {

        mockValidateProjectId();
        mockFindById();
        mockServiceRequestClient();
        mockMdms();
        mockProjectFindIds();

        projectBeneficiaryService.update(request);

        verify(projectService, times(1)).validateProjectIds(any(List.class));
    }

    @Test
    @DisplayName("should throw exception for any invalid product id")
    void shouldThrowExceptionForAnyInvalidProductId() throws Exception {
       when(projectService.validateProjectIds(any(List.class))).thenReturn(Collections.emptyList());

        assertThrows(CustomException.class, () -> projectBeneficiaryService.update(request));
    }

    @Test
    @DisplayName("should fetch existing records using id")
    void shouldFetchExistingRecordsUsingId() throws Exception {

        mockValidateProjectId();
        mockFindById();
        mockServiceRequestClient();
        mockMdms();
        mockProjectFindIds();

        projectBeneficiaryService.update(request);

        verify(projectBeneficiaryRepository, times(1)).findById(anyList());
    }

    @Test
    @DisplayName("should throw exception if fetched records count doesn't match the count in request")
    void shouldThrowExceptionIfFetchedRecordsCountDoesntMatchTheCountInRequest() throws Exception {

        mockValidateProjectId();
        mockFindById();
        mockServiceRequestClient();
        mockMdms();
        mockProjectFindIds();

        when(projectBeneficiaryRepository.findById(anyList())).thenReturn(Collections.emptyList());

        assertThrows(CustomException.class, () -> projectBeneficiaryService.update(request));
    }

    @Test
    @DisplayName("should send the updates to kafka")
    void shouldSendTheUpdatesToKafka() throws Exception {

        mockValidateProjectId();
        mockFindById();
        mockServiceRequestClient();
        mockMdms();
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
        mockFindById();
        mockServiceRequestClient();
        mockMdms();
        mockProjectFindIds();

        assertThrows(Exception.class, () -> projectBeneficiaryService.update(beneficiaryRequest));
    }
}
