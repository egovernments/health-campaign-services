package org.egov.transformer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.Target;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.ProjectIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.transformer.Transformer;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.egov.transformer.Constants.*;

@Slf4j
public abstract class ProjectTransformationService implements TransformationService<Project> {
    protected final ProjectIndexV1Transformer transformer;

    protected final Producer producer;

    protected final TransformerProperties properties;

    @Autowired
    protected ProjectTransformationService(ProjectIndexV1Transformer transformer,
                                           Producer producer, TransformerProperties properties) {
        this.transformer = transformer;
        this.producer = producer;
        this.properties = properties;
    }

    @Override
    public void transform(List<Project> payloadList) {
        log.info("transforming for ids {}", payloadList.stream()
                .map(Project::getId).collect(Collectors.toList()));
        List<ProjectIndexV1> transformedPayloadList = payloadList.stream()
                .map(transformer::transform)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        log.info("transformation successful");
        producer.push(getTopic(),
                transformedPayloadList);
    }

    public abstract String getTopic();

    @Override
    public Operation getOperation() {
        return Operation.PROJECT;
    }

    @Component
    static class ProjectIndexV1Transformer implements
            Transformer<Project, ProjectIndexV1> {
        private final ProjectService projectService;
        private final ProductService productService;
        private final ObjectMapper objectMapper;
        private final CommonUtils commonUtils;


        @Autowired
        ProjectIndexV1Transformer(ProjectService projectService, ProductService productService, ObjectMapper objectMapper, CommonUtils commonUtils) {
            this.projectService = projectService;
            this.productService = productService;
            this.objectMapper = objectMapper;
            this.commonUtils = commonUtils;
        }

        @Override
        public List<ProjectIndexV1> transform(Project project) {
            String tenantId = project.getTenantId();
            String projectTypeId = project.getProjectTypeId();
            JsonNode mdmsBoundaryData = projectService.fetchBoundaryData(tenantId, null, projectTypeId);
            List<JsonNode> boundaryLevelVsLabel = StreamSupport
                    .stream(mdmsBoundaryData.get(BOUNDARY_HIERARCHY).spliterator(), false).collect(Collectors.toList());
            Map<String, String> boundaryLabelToNameMap = projectService
                    .getBoundaryLabelToNameMap(project.getAddress().getBoundary(), tenantId);
            log.info("boundary labels {}", boundaryLabelToNameMap.toString());
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

            String projectBeneficiaryType = projectService.getProjectBeneficiaryType(tenantId, projectTypeId);

            return targets.stream().map(r -> {
                        Long startDate = project.getStartDate();
                        Long endDate = project.getEndDate();
                        Integer targetNo = r.getTargetNo();
                        Integer campaignDurationInDays = null;
                        Integer targetPerDay = null;
                        Long milliSecForOneDay = (long) (24 * 60 * 60 * 1000);
                        if (startDate != null && endDate != null) {
                            campaignDurationInDays = (int) ((endDate - startDate) / milliSecForOneDay);
                            if (targetNo != null && campaignDurationInDays > 0) {
                                targetPerDay = targetNo / campaignDurationInDays;
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
                                .createdTime(project.getAuditDetails().getCreatedTime())
                                .createdBy(project.getAuditDetails().getCreatedBy())
                                .lastModifiedTime(project.getAuditDetails().getLastModifiedTime())
                                .lastModifiedBy(project.getAuditDetails().getLastModifiedBy())
                                .build();
                        if (projectIndexV1.getBoundaryHierarchy() == null) {
                            ObjectNode boundaryHierarchy = objectMapper.createObjectNode();
                            projectIndexV1.setBoundaryHierarchy(boundaryHierarchy);
                        }
                        boundaryLevelVsLabel.stream()
                                .filter(node -> node.get(LEVEL).asInt() > 1)
                                .forEach(node -> {
                                    String label = node.get(INDEX_LABEL).asText();
                                    String name = Optional.ofNullable(boundaryLabelToNameMap.get(node.get(LABEL).asText()))
                                            .orElse(null);
                                    projectIndexV1.getBoundaryHierarchy().put(label, name);
                                });
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
}
