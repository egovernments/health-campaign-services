package org.egov.transformer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.tracer.model.CustomException;
import org.egov.transformer.config.TransformerProperties;
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

@Component
@Slf4j
public class ProjectService {

    private final TransformerProperties transformerProperties;

    private final ServiceRequestClient serviceRequestClient;

    private final ObjectMapper objectMapper;

    private static Map<String, Project> projectMap = new ConcurrentHashMap<>();

    public ProjectService(TransformerProperties transformerProperties,
                          ServiceRequestClient serviceRequestClient,
                          ObjectMapper objectMapper) {
        this.transformerProperties = transformerProperties;
        this.serviceRequestClient = serviceRequestClient;
        this.objectMapper = objectMapper;
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

    public List<Project> searchProject(String projectId, String tenantId) {

        ProjectRequest request = ProjectRequest.builder()
                .projects(Collections.singletonList(Project.builder().id(projectId).tenantId(tenantId).build()))
                .build();

        ProjectResponse response;
        try {
            response = serviceRequestClient.fetchResult(
                    new StringBuilder(transformerProperties.getProjectHost()
                            + transformerProperties.getProjectSearchUrl()
                            + "?limit=" + transformerProperties.getSearchApiLimit()
                            + "&offset=0&tenantId=" + tenantId),
                    request,
                    ProjectResponse.class);
        } catch (Exception e) {
            log.error("error while fetching facility list", e);
            throw new CustomException("PROJECT_FETCH_ERROR",
                    "Error while fetching project details for id: " + projectId);
        }
        return response.getProject();
    }
}
