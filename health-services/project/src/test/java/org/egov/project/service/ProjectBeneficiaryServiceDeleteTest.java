package org.egov.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.models.project.BeneficiaryBulkRequest;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.service.IdGenService;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.helper.BeneficiaryBulkRequestTestBuilder;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.egov.project.service.enrichment.ProjectBeneficiaryEnrichmentService;
import org.egov.project.validator.beneficiary.PbNonExistentEntityValidator;
import org.egov.project.validator.beneficiary.PbNullIdValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectBeneficiaryServiceDeleteTest {

    @InjectMocks
    private ProjectBeneficiaryService projectBeneficiaryService;

    @Mock
    private IdGenService idGenService;

    @Mock
    private ProjectBeneficiaryRepository projectBeneficiaryRepository;

    @Mock
    private PbNullIdValidator pbNullIdValidator;

    @Mock
    private PbNonExistentEntityValidator pbNonExistentEntityValidator;

    private List<Validator<BeneficiaryBulkRequest, ProjectBeneficiary>> validators;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ProjectConfiguration projectConfiguration;

    @Mock
    private ProjectBeneficiaryEnrichmentService projectBeneficiaryEnrichmentService;

    private BeneficiaryBulkRequest request;


    @BeforeEach
    void setUp() {
        request = BeneficiaryBulkRequestTestBuilder.builder()
                .withOneProjectBeneficiary()
                .build();
        validators = Arrays.asList(pbNullIdValidator, pbNonExistentEntityValidator);
        ReflectionTestUtils.setField(projectBeneficiaryService, "validators", validators);
        ReflectionTestUtils.setField(projectBeneficiaryService, "isApplicableForDelete",
                (Predicate<Validator<BeneficiaryBulkRequest, ProjectBeneficiary>>) validator ->
                validator.getClass().equals(PbNullIdValidator.class)
                        || validator.getClass().equals(PbNonExistentEntityValidator.class));
        when(projectConfiguration.getDeleteProjectBeneficiaryTopic()).thenReturn("delete-topic");
    }

    @Test
    @DisplayName("should delete the project beneficiary")
    void shouldDeleteTheProjectBeneficiary() {
        projectBeneficiaryService.delete(request, false);
        verify(projectBeneficiaryRepository, times(1)).save(anyList(), anyString());
    }
}
