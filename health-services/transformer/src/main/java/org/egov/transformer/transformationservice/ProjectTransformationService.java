package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.Target;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.downstream.ProjectIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.ProductService;
import org.egov.transformer.service.ProjectService;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class ProjectTransformationService {
    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final ObjectMapper objectMapper;
    private final CommonUtils commonUtils;
    private final ProjectService projectService;
    private final ProductService productService;

    public ProjectTransformationService(TransformerProperties transformerProperties, Producer producer, ObjectMapper objectMapper, CommonUtils commonUtils, ProjectService projectService, ProductService productService) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.commonUtils = commonUtils;
        this.projectService = projectService;
        this.productService = productService;
    }


    public void transform(List<Project> projectList) {
        log.info("transforming for PROJECT id's {}", projectList.stream()
                .map(Project::getId).collect(Collectors.toList()));
        String topic = transformerProperties.getTransformerProducerBulkProjectIndexV1Topic();
        List<ProjectIndexV1> projectIndexV1List = projectList.stream()
                .map(this::transform)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        log.info("transformation success for PROJECT id's {}", projectIndexV1List.stream()
                .map(ProjectIndexV1::getId)
                .collect(Collectors.toList()));
        producer.push(topic, projectIndexV1List);
    }

    private List<ProjectIndexV1> transform(Project project) {
        String localityCode;
        if (project.getAddress() != null) {
            localityCode = project.getAddress().getBoundary() != null ?
                    project.getAddress().getBoundary() :
                    project.getAddress().getLocality() != null ?
                            project.getAddress().getLocality().getCode() :
                            null;
        } else {
            localityCode = null;
        }
        Map<String, String> boundaryHierarchy = localityCode != null ?
                projectService.getBoundaryHierarchyWithLocalityCode(project.getAddress().getBoundary(), project.getTenantId()) : null;
        String tenantId = project.getTenantId();
        String projectTypeId = project.getProjectTypeId();
        List<Target> targets = project.getTargets();
        Set<String> fieldsToCheck = new HashSet<>(Arrays.asList(
                BENEFICIARY_TYPE,
                TOTAL_NO_CHECK,
                TARGET_NO_CHECK
        ));
        if (targets == null || targets.isEmpty()) {
            return Collections.emptyList();
        }
        isValidTargetsAdditionalDetails(project, targets, FIELD_TARGET, fieldsToCheck, BENEFICIARY_TYPE);

        JsonNode additionalDetails = projectService.fetchProjectAdditionalDetails(tenantId, null, projectTypeId);

        String projectBeneficiaryType = projectService.getProjectBeneficiaryType(tenantId, projectTypeId);

        return targets.stream().map(r -> {
                    Long startDate = project.getStartDate();
                    Long endDate = project.getEndDate();
                    Integer targetNo = r.getTargetNo();
                    Integer campaignDurationInDays = null;
                    Integer targetPerDay = null;
                    Long milliSecForOneDay = (long) (24 * 60 * 60 * 1000);
                    if(transformerProperties.getProjectTargetNumberType().equals(PROJECT_TARGET_NUMBER_TYPE_PER_DAY)) {
                        targetPerDay = targetNo;
                    } else if (transformerProperties.getProjectTargetNumberType().equals(PROJECT_TARGET_NUMBER_TYPE_OVERALL)){
                        if (startDate != null && endDate != null) {
                            campaignDurationInDays = (int) ((endDate - startDate) / milliSecForOneDay);
                            if (targetNo != null && campaignDurationInDays > 0) {
                                targetPerDay = targetNo / campaignDurationInDays;
                            }
                        }
                    }

                    List<String> productVariants = projectService.getProducts(tenantId, project.getProjectTypeId());
                    String productVariantName = String.join(COMMA, productService.getProductVariantNames(productVariants, project.getTenantId()));
                    String productVariant = null;
                    if (productVariants != null && !productVariants.isEmpty()) {
                        productVariant = String.join(COMMA, productVariants);
                    }
                    if (r.getId() == null) {
                        r.setId(project.getId() + HYPHEN + r.getBeneficiaryType());
                    }

                    ProjectIndexV1 projectIndexV1 = ProjectIndexV1.builder()
                            .id(r.getId())
                            .projectId(project.getId())
                            .projectBeneficiaryType(projectBeneficiaryType)
                            .overallTarget(targetNo)
                            .targetPerDay(targetPerDay)
                            .campaignDurationInDays(campaignDurationInDays)
                            .startDate(project.getStartDate())
                            .endDate(project.getEndDate())
                            .productVariant(productVariant)
                            .productName(productVariantName)
                            .targetType(r.getBeneficiaryType())
                            .tenantId(tenantId)
                            .taskDates(commonUtils.getProjectDatesList(project.getStartDate(), project.getEndDate()))
                            .projectType(project.getProjectType())
                            .subProjectType(project.getProjectSubType())
                            .projectTypeId(projectTypeId)
                            .localityCode(localityCode)
                            .createdTime(project.getAuditDetails().getCreatedTime())
                            .createdBy(project.getAuditDetails().getCreatedBy())
                            .additionalDetails(additionalDetails)
                            .boundaryHierarchy(boundaryHierarchy)
                            .build();
                    return projectIndexV1;
                }
        ).collect(Collectors.toList());
    }

    private void isValidTargetsAdditionalDetails(Project project, List<Target> targets, String fieldTarget, Set<String> fieldsToCheck, String beneficiaryType) {
        if (project.getAdditionalDetails() != null) {
            JsonNode additionalDetails = objectMapper.valueToTree(project.getAdditionalDetails());
            Set<String> beneficiaryTypes = targets.stream().map(Target::getBeneficiaryType).collect(Collectors.toSet());
            if (additionalDetails.hasNonNull(fieldTarget)) {
                JsonNode targetArray = additionalDetails.get(fieldTarget);
                if (targetArray.isArray() && !targetArray.isEmpty()) {
                    targetArray.forEach(target -> {
                        Iterator<String> fieldIterator = target.fieldNames();
                        Iterable<String> iterable = () -> fieldIterator;
                        Set<String> actualList = StreamSupport
                                .stream(iterable.spliterator(), false)
                                .collect(Collectors.toSet());
                        if (actualList.containsAll(fieldsToCheck)) {
                            if (!beneficiaryTypes.contains(target.get(beneficiaryType).asText())) {
                                try {
                                    targets.add(objectMapper.treeToValue(target, Target.class));

                                } catch (JsonProcessingException e) {
                                    log.error("target object : " + target + " could not be processed {}", ExceptionUtils.getStackTrace(e));
                                }
                            }
                        }
                    });
                }
            }
        }
    }
}
