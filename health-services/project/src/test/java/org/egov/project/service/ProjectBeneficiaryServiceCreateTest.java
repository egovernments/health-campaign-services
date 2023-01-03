package org.egov.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.UserSearchRequest;
import digit.models.coremodels.mdms.MdmsCriteriaReq;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.service.IdGenService;
import org.egov.common.service.MdmsService;
import org.egov.common.service.UserService;
import org.egov.project.helper.BeneficiaryRequestTestBuilder;
import org.egov.project.helper.ProjectStaffRequestTestBuilder;
import org.egov.project.repository.ProjectBeneficiaryRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectBeneficiaryServiceCreateTest {

    @InjectMocks
    private ProjectBeneficiaryService projectBeneficiaryService;

    @Mock
    private ProjectService projectService;

    @Mock
    private IdGenService idGenService;

    @Mock
    private MdmsService mdmsService;

    @Mock
    private ServiceRequestClient serviceRequestClient;

    @Mock
    private ProjectBeneficiaryRepository projectBeneficiaryRepository;


    private BeneficiaryRequest request;

    @BeforeEach
    void setUp() throws Exception {
        request = BeneficiaryRequestTestBuilder.builder()
                .withOneProjectBeneficiary()
                .build();
        List<String> idList = new ArrayList<>();
        idList.add("some-id");
        lenient().when(idGenService.getIdList(any(RequestInfo.class),
                any(String.class),
                eq("project.beneficiary.id"), eq(""), anyInt()))
                .thenReturn(idList);
    }

    private void mockValidateProjectId() {
        lenient().when(projectService.validateProjectIds(any(List.class)))
                .thenReturn(Collections.singletonList("some-project-id"));
    }
    private void mockProjectFindIds() {
        when(projectService.findByIds(any(List.class))).thenReturn(Collections.singletonList(
                Project.builder().id("some-project-id").projectTypeId("some-project-type-id").build()));
    }


    @Test
    @DisplayName("should enrich the formatted id in project staff")
    void shouldEnrichTheFormattedIdInProductVariants() throws Exception {
        mockValidateProjectId();
        mockMdms();
        mockProjectFindIds();
        mockServiceRequestClient();

        List<ProjectBeneficiary> projectBeneficiaries = projectBeneficiaryService.create(request);

        assertEquals("some-id", projectBeneficiaries.get(0).getId());
    }

    @Test
    @DisplayName("should send the enriched project staff to the kafka topic")
    void shouldSendTheEnrichedProjectStaffToTheKafkaTopic() throws Exception {
        mockValidateProjectId();
        mockMdms();
        mockProjectFindIds();
        mockServiceRequestClient();

        projectBeneficiaryService.create(request);

        verify(idGenService, times(1)).getIdList(any(RequestInfo.class),
                any(String.class),
                eq("project.beneficiary.id"), eq(""), anyInt());
        verify(projectBeneficiaryRepository, times(1)).save(any(List.class), any(String.class));
    }

    @Test
    @DisplayName("should update audit details before pushing the project staff to kafka")
    void shouldUpdateAuditDetailsBeforePushingTheProjectStaffsToKafka() throws Exception {
        mockValidateProjectId();
        mockMdms();
        mockProjectFindIds();
        mockServiceRequestClient();

        List<ProjectBeneficiary> projectBeneficiaries = projectBeneficiaryService.create(request);

        assertNotNull(projectBeneficiaries.stream().findAny().get().getAuditDetails().getCreatedBy());
        assertNotNull(projectBeneficiaries.stream().findAny().get().getAuditDetails().getCreatedTime());
        assertNotNull(projectBeneficiaries.stream().findAny().get().getAuditDetails().getLastModifiedBy());
        assertNotNull(projectBeneficiaries.stream().findAny().get().getAuditDetails().getLastModifiedTime());
    }

    @Test
    @DisplayName("should set row version as 1 and deleted as false")
    void shouldSetRowVersionAs1AndDeletedAsFalse() throws Exception {
        mockValidateProjectId();
        mockMdms();
        mockProjectFindIds();
        mockServiceRequestClient();


        List<ProjectBeneficiary> projectBeneficiaries = projectBeneficiaryService.create(request);

        assertEquals(1, projectBeneficiaries.stream().findAny().get().getRowVersion());
        assertFalse(projectBeneficiaries.stream().findAny().get().getIsDeleted());
    }



    @Test
    @DisplayName("should validate correct project id")
    void shouldValidateCorrectProjectId() throws Exception {
        mockValidateProjectId();
        mockMdms();
        mockProjectFindIds();
        mockServiceRequestClient();

        projectBeneficiaryService.create(request);

        verify(projectService, times(1)).validateProjectIds(any(List.class));
    }

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

    @Test
    @DisplayName("should throw exception for any invalid project id")
    void shouldThrowExceptionForAnyInvalidProjectId() throws Exception {

        when(projectService.validateProjectIds(any(List.class))).thenReturn(Collections.emptyList());

        assertThrows(CustomException.class, () -> projectBeneficiaryService.create(request));
    }

}