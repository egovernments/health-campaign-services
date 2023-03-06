package org.egov.transformer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.tracer.model.CustomException;
import org.egov.transformer.boundary.BoundaryNode;
import org.egov.transformer.boundary.BoundaryTree;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.egov.transformer.models.upstream.Boundary;
import org.egov.transformer.models.upstream.Project;
import org.egov.transformer.models.upstream.ProjectRequest;
import org.egov.transformer.models.upstream.ProjectResponse;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ProjectService {

    private final TransformerProperties transformerProperties;

    private final ServiceRequestClient serviceRequestClient;

    private final ObjectMapper objectMapper;

    private final BoundaryService boundaryService;

    private static final Map<String, Project> projectMap = new ConcurrentHashMap<>();

    public ProjectService(TransformerProperties transformerProperties,
                          ServiceRequestClient serviceRequestClient,
                          ObjectMapper objectMapper, BoundaryService boundaryService) {
        this.transformerProperties = transformerProperties;
        this.serviceRequestClient = serviceRequestClient;
        this.objectMapper = objectMapper;
        this.boundaryService = boundaryService;
    }

    @KafkaListener(topics = "${transformer.consumer.update.project.topic}")
    public void bulkCreate(ConsumerRecord<String, Object> payload,
                           @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            ProjectRequest projectRequest = objectMapper.readValue((String) payload.value(), ProjectRequest.class);
            projectRequest.getProjects().forEach(project -> projectMap.put(project.getId(), project));
        } catch (Exception exception) {
            log.error("error in project update consumer", exception);
        }
    }

    public Project getProject(String projectId, String tenantId) {
        if (projectMap.containsKey(projectId)) {
            log.info("getting project {} from cache", projectId);
            return projectMap.get(projectId);
        }
        List<Project> projects = searchProject(projectId, tenantId);
        Project project = null;
        if (!projects.isEmpty()) {
            project = projects.get(0);
            projectMap.put(projectId, project);
        }
        return project;
    }

    public Map<String, String> getBoundaryLabelToNameMap(String projectId, String tenantId) {
        Project project = getProject(projectId, tenantId);
        String locationCode = project.getAddress().getBoundary();
        List<Boundary> boundaryList = boundaryService.getBoundary(locationCode, "ADMIN",
                project.getTenantId());
        BoundaryTree boundaryTree = boundaryService.generateTree(boundaryList.get(0));
        BoundaryTree locationTree = boundaryService.search(boundaryTree, locationCode);
        List<BoundaryNode> parentNodes = locationTree.getParentNodes();
        Map<String, String> resultMap = parentNodes.stream().collect(Collectors
                .toMap(BoundaryNode::getLabel, BoundaryNode::getName));
        resultMap.put(locationTree.getBoundaryNode().getLabel(), locationTree.getBoundaryNode().getName());
        return resultMap;
    }

    private List<Project> searchProject(String projectId, String tenantId) {

        ProjectRequest request = ProjectRequest.builder()
                .requestInfo(RequestInfo.builder().
                userInfo(User.builder()
                        .uuid("transformer-uuid")
                        .build())
                .build())
                .projects(Collections.singletonList(Project.builder().id(projectId).tenantId(tenantId).build()))
                .build();

        ProjectResponse response;
        try {
            StringBuilder uri = new StringBuilder();
            uri.append(transformerProperties.getProjectHost())
                    .append(transformerProperties.getProjectSearchUrl())
                    .append("?limit=").append(transformerProperties.getSearchApiLimit())
                    .append("&offset=0")
                    .append("&tenantId=").append(tenantId);
            response = serviceRequestClient.fetchResult(uri,
                    request,
                    ProjectResponse.class);
        } catch (Exception e) {
            log.error("error while fetching project list", e);
            throw new CustomException("PROJECT_FETCH_ERROR",
                    "error while fetching project details for id: " + projectId);
        }
        return response.getProject();
    }
}
