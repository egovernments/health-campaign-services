package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.Target;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.ProjectIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.producer.TransformerErrorProducer;
import org.egov.transformer.service.BoundaryService;
import org.egov.transformer.service.ProductService;
import org.egov.transformer.service.ProjectFactoryService;
import org.egov.transformer.service.ProjectService;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

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
    private final BoundaryService boundaryService;
    private final ProjectFactoryService projectFactoryService;
    private final TransformerErrorProducer errorProducer;

    private static final Set<String> TARGET_FIELDS_TO_CHECK = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            BENEFICIARY_TYPE,
            TOTAL_NO_CHECK,
            TARGET_NO_CHECK
    )));

    public ProjectTransformationService(TransformerProperties transformerProperties, Producer producer, ObjectMapper objectMapper, CommonUtils commonUtils, ProjectService projectService, ProductService productService, BoundaryService boundaryService, ProjectFactoryService projectFactoryService, TransformerErrorProducer errorProducer) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.commonUtils = commonUtils;
        this.projectService = projectService;
        this.productService = productService;
        this.boundaryService = boundaryService;
        this.projectFactoryService = projectFactoryService;
        this.errorProducer = errorProducer;
    }


    public void transform(List<Project> projectList) {
        log.info("transforming for PROJECT id's {}", projectList.stream()
                .map(Project::getId).collect(Collectors.toList()));
        String topic = transformerProperties.getTransformerProducerBulkProjectIndexV1Topic();
        List<ProjectIndexV1> projectIndexV1List = projectList.stream()
                .map(this::transform)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        log.info("transformation success for {} PROJECT records producing {} index records",
                projectList.size(), projectIndexV1List.size());
        log.debug("transformed PROJECT index id's {}", projectIndexV1List.stream()
                .map(ProjectIndexV1::getId)
                .collect(Collectors.toList()));
        producer.pushInBatches(topic, projectIndexV1List);
    }

    private List<ProjectIndexV1> transform(Project project) {
        String localityCode;
        // Converted once and threaded through: hierarchy type, cycle details and product resources all read
        // from this same tree, which was previously rebuilt for each of them.
        JsonNode projectAdditionalDetails = objectMapper.valueToTree(project.getAdditionalDetails());
        String hierarhyType = projectService.getHierarchyTypeFromProject(project, projectAdditionalDetails);
        if (project.getAddress() != null) {
            localityCode = project.getAddress().getBoundary() != null ?
                    project.getAddress().getBoundary() :
                    project.getAddress().getLocality() != null ?
                            project.getAddress().getLocality().getCode() :
                            null;
        } else {
            localityCode = null;
        }
        BoundaryHierarchyResult boundaryHierarchyResult = getBoundaryHierarchyResult(localityCode, project.getTenantId(), hierarhyType);

        Map<String, String> boundaryHierarchy =  boundaryHierarchyResult.getBoundaryHierarchy();
        Map<String, String> boundaryHierarchyCode =  boundaryHierarchyResult.getBoundaryHierarchyCode();

        String tenantId = project.getTenantId();
        String projectTypeId = project.getProjectTypeId();
        if (CollectionUtils.isEmpty(project.getTargets())) {
            return Collections.emptyList();
        }
        // A new list rather than mutating project.getTargets(): appending to the payload's own list would
        // duplicate targets on any re-transform of the same Project instance.
        List<Target> targets = new ArrayList<>(project.getTargets());

//        Commenting as we are not using it now
//        targets.addAll(extraTargetsFromAdditionalDetails(projectAdditionalDetails, targets, FIELD_TARGET,
//                TARGET_FIELDS_TO_CHECK, BENEFICIARY_TYPE));

        JsonNode additionalDetails = projectService.fetchProjectAdditionalDetails(projectAdditionalDetails);

        String projectBeneficiaryType = projectService.getProjectBeneficiaryType(tenantId, projectTypeId);

        String campaignId;
        if (StringUtils.isNotBlank(project.getReferenceID())) {
            campaignId = projectFactoryService.getCampaignIdFromCampaignNumber(
                    project.getTenantId(), true, project.getReferenceID()
            );
        } else {
            campaignId = null;
        }

        // Everything below is identical for every target of this project, so it is resolved once here
        // instead of inside the loop. getProducts in particular was one MDMS call per target.
        List<String> productVariants = projectService.getProducts(tenantId, projectTypeId, projectAdditionalDetails);
        String productVariantName = String.join(COMMA, productService.getProductVariantNames(productVariants, tenantId));
        String productVariant = CollectionUtils.isEmpty(productVariants) ? null : String.join(COMMA, productVariants);
        Long startDate = project.getStartDate();
        Long endDate = project.getEndDate();
        List<String> taskDates = startDate == null || endDate == null
                ? Collections.emptyList() : commonUtils.getProjectDatesList(startDate, endDate);
        String targetNumberType = transformerProperties.getProjectTargetNumberType();
        Integer campaignDurationInDays = null;
        if (PROJECT_TARGET_NUMBER_TYPE_OVERALL.equals(targetNumberType) && startDate != null && endDate != null) {
            campaignDurationInDays = (int) ((endDate - startDate) / DAY_MILLIS);
        }
        final Integer projectCampaignDurationInDays = campaignDurationInDays;


        return targets.stream().map(r -> {
                    Integer targetNo = r.getTargetNo();
                    Integer targetPerDay = null;
                    if (PROJECT_TARGET_NUMBER_TYPE_PER_DAY.equals(targetNumberType)) {
                        targetPerDay = targetNo;
                    } else if (PROJECT_TARGET_NUMBER_TYPE_OVERALL.equals(targetNumberType)
                            && targetNo != null && projectCampaignDurationInDays != null
                            && projectCampaignDurationInDays > 0) {
                        targetPerDay = targetNo / projectCampaignDurationInDays;
                    }
                    if (r.getId() == null) {
                        r.setId(project.getId() + HYPHEN + r.getBeneficiaryType());
                    }

                    ProjectIndexV1 projectIndexV1 = ProjectIndexV1.builder()
                            .id(r.getId())
                            .projectBeneficiaryType(projectBeneficiaryType)
                            .overallTarget(targetNo)
                            .targetPerDay(targetPerDay)
                            .campaignDurationInDays(projectCampaignDurationInDays)
                            .startDate(startDate)
                            .endDate(endDate)
                            .productVariant(productVariant)
                            .productName(productVariantName)
                            .targetType(r.getBeneficiaryType())
                            .tenantId(tenantId)
                            .taskDates(taskDates)
                            .subProjectType(project.getProjectSubType())
                            .localityCode(localityCode)
                            .createdTime(project.getAuditDetails().getCreatedTime())
                            .createdBy(project.getAuditDetails().getCreatedBy())
                            .additionalDetails(additionalDetails)
                            .boundaryHierarchy(boundaryHierarchy)
                            .boundaryHierarchyCode(boundaryHierarchyCode)
                            .referenceID(project.getReferenceID())
                            .projectNumber(project.getProjectNumber())
                            .build();
                    projectIndexV1.setProjectInfo(project.getId(), project.getProjectType(), projectTypeId, project.getName(),hierarhyType);
                    projectIndexV1.setCampaignNumber(project.getReferenceID());
                    projectIndexV1.setCampaignId(campaignId);
                    return projectIndexV1;
                }
        ).collect(Collectors.toList());
    }

    /**
     * Targets declared in additionalDetails that are not already among the project's own targets. Returns
     * them instead of appending to the caller's list, so the incoming payload is never mutated.
     */
    private List<Target> extraTargetsFromAdditionalDetails(JsonNode projectAdditionalDetails, List<Target> targets,
                                                           String fieldTarget, Set<String> fieldsToCheck,
                                                           String beneficiaryType) {
        List<Target> extraTargets = new ArrayList<>();
        if (projectAdditionalDetails == null || !projectAdditionalDetails.hasNonNull(fieldTarget)) {
            return extraTargets;
        }
        JsonNode targetArray = projectAdditionalDetails.get(fieldTarget);
        if (!targetArray.isArray() || targetArray.isEmpty()) {
            return extraTargets;
        }
        Set<String> beneficiaryTypes = targets.stream().map(Target::getBeneficiaryType).collect(Collectors.toSet());
        for (JsonNode target : targetArray) {
            Iterator<String> fieldIterator = target.fieldNames();
            Iterable<String> iterable = () -> fieldIterator;
            Set<String> actualFields = StreamSupport.stream(iterable.spliterator(), false).collect(Collectors.toSet());
            if (!actualFields.containsAll(fieldsToCheck)
                    || beneficiaryTypes.contains(target.get(beneficiaryType).asText())) {
                continue;
            }
            try {
                extraTargets.add(objectMapper.treeToValue(target, Target.class));
            } catch (JsonProcessingException e) {
                log.error("target object : " + target + " could not be processed {}", ExceptionUtils.getStackTrace(e));
                errorProducer.sendToErrorTopic(target, null, e);
            }
        }
        return extraTargets;
    }

    private BoundaryHierarchyResult getBoundaryHierarchyResult(String localityCode, String tenantId,String hierarhyType) {
        if (localityCode != null) {
            return boundaryService.getBoundaryHierarchyWithLocalityCode(localityCode, tenantId,hierarhyType);
        }
        return new BoundaryHierarchyResult();
    }
}
