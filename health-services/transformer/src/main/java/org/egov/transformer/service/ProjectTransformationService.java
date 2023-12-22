package org.egov.transformer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.Target;
import org.egov.transformer.Constants;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.ProjectIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.transformer.Transformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.egov.transformer.Constants.HYPHEN;

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
        private final TransformerProperties properties;
        private final ObjectMapper objectMapper;


        @Autowired
        ProjectIndexV1Transformer(ProjectService projectService, TransformerProperties properties, ObjectMapper objectMapper) {
            this.projectService = projectService;
            this.properties = properties;
            this.objectMapper = objectMapper;
        }

        @Override
        public List<ProjectIndexV1> transform(Project project) {
            String tenantId = project.getTenantId();
            String projectType = project.getProjectType();
            String subProjectType = project.getProjectSubType();
            String projectTypeId = project.getProjectTypeId();
            JsonNode mdmsBoundaryData = projectService.fetchBoundaryData(tenantId, null,projectTypeId);
            List<JsonNode> boundaryLevelVsLabel = StreamSupport
                    .stream(mdmsBoundaryData.get(Constants.BOUNDARY_HIERARCHY).spliterator(), false).collect(Collectors.toList());
            Map<String, String> boundaryLabelToNameMap = projectService
                    .getBoundaryLabelToNameMap(project.getAddress().getBoundary(), project.getTenantId());
            log.info("boundary labels {}", boundaryLabelToNameMap.toString());
            List<Target> targets = project.getTargets();
            Set<String> fieldsToCheck = new HashSet<>();
            fieldsToCheck.add(Constants.BENEFICIARY_TYPE);
            fieldsToCheck.add(Constants.TOTAL_NO_CHECK);
            fieldsToCheck.add(Constants.TARGET_NO_CHECK);
            if (targets == null || targets.isEmpty()) {
                return Collections.emptyList();
            }
            isValidTargetsAdditionalDetails(project, targets, Constants.FIELD_TARGET, fieldsToCheck, Constants.BENEFICIARY_TYPE);

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

                        List<String> productVariants = projectService.getProducts(project.getTenantId(),
                                project.getProjectTypeId());
                        String productVariant = null;
                        if (productVariants != null && !productVariants.isEmpty()) {
                            productVariant = String.join(",", productVariants);
                        }
                        if (r.getId() == null) {
                            r.setId(project.getId() + HYPHEN + r.getBeneficiaryType());
                        }

                        ProjectIndexV1 projectIndexV1 = ProjectIndexV1.builder()
                                .id(r.getId())
                                .projectId(project.getId())
                                .overallTarget(targetNo)
                                .targetPerDay(targetPerDay)
                                .campaignDurationInDays(campaignDurationInDays)
                                .startDate(project.getStartDate())
                                .endDate(project.getEndDate())
                                .productVariant(productVariant)
                                .targetType(r.getBeneficiaryType())
                                .tenantId(tenantId)
                                .projectType(projectType)
                                .subProjectType(subProjectType)
                                .createdTime(project.getAuditDetails().getCreatedTime())
                                .createdBy(project.getAuditDetails().getCreatedBy())
                                .lastModifiedTime(project.getAuditDetails().getLastModifiedTime())
                                .lastModifiedBy(project.getAuditDetails().getLastModifiedBy())
                                .build();
                        if (projectIndexV1.getBoundaryHierarchy() == null) {
                            ObjectNode boundaryHierarchy = objectMapper.createObjectNode();
                            projectIndexV1.setBoundaryHierarchy(boundaryHierarchy);
                        }
                        boundaryLevelVsLabel.forEach(node -> {
                            if (node.get(Constants.LEVEL).asInt() > 1) {
                                projectIndexV1.getBoundaryHierarchy().put(node.get(Constants.INDEX_LABEL).asText(),boundaryLabelToNameMap.get(node.get(Constants.LABEL).asText()) == null ? null :  boundaryLabelToNameMap.get(node.get(Constants.LABEL).asText()));
                            }
                        });
                        return projectIndexV1;
                    }
            ).collect(Collectors.toList());
        }

        private void isValidTargetsAdditionalDetails(Project project, List<Target> targets, String fieldTarget, Set<String> fieldsToCheck, String beneficiaryType) {
            if(project.getAdditionalDetails()!=null){
                JsonNode additionalDetails = objectMapper.valueToTree(project.getAdditionalDetails());
                Set<String> beneficiaryTypes = targets.stream().map(Target::getBeneficiaryType).collect(Collectors.toSet());
                if(additionalDetails.hasNonNull(fieldTarget)){
                    JsonNode targetArray = additionalDetails.get(fieldTarget);
                    if(targetArray.isArray() && !targetArray.isEmpty()) {
                        targetArray.forEach(target->{
                            Iterator<String> fieldIterator = target.fieldNames();
                            Iterable<String> iterable = () -> fieldIterator;
                            Set<String> actualList = StreamSupport
                                    .stream(iterable.spliterator(), false)
                                    .collect(Collectors.toSet());
                            if(actualList.containsAll(fieldsToCheck)){
                                if(!beneficiaryTypes.contains(target.get(beneficiaryType).asText())){
                                    try {
                                        targets.add(objectMapper.treeToValue(target,Target.class));

                                    } catch (JsonProcessingException e) {
                                        log.error("target object :" + target + " could not be processed {}", ExceptionUtils.getStackTrace(e));
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
