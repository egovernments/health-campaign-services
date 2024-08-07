package org.egov.project.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import digit.models.coremodels.AuditDetails;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.egov.common.models.project.Project;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static java.util.Objects.isNull;

@Component
public class ProjectServiceUtil {
    @Autowired
    private ObjectMapper objectMapper;

    public AuditDetails getAuditDetails(String by, AuditDetails auditDetails, Boolean isCreate) {
        Long time = System.currentTimeMillis();
        if (isCreate)
            return AuditDetails.builder().createdBy(by).lastModifiedBy(by).createdTime(time).lastModifiedTime(time).build();
        else
            return AuditDetails.builder().createdBy(auditDetails.getCreatedBy()).lastModifiedBy(by)
                    .createdTime(auditDetails.getCreatedTime()).lastModifiedTime(time).build();
    }


    /**
     * Creates a map from a list of projects, using project IDs as keys.
     *
     * @param projects The list of projects to be converted into a map.
     * @return A map with project IDs as keys and project objects as values.
     */
    public Map<String, Project> createProjectMap(List<Project> projects) {
        return projects.stream()
            .collect(Collectors.toMap(p -> String.valueOf(p.getId()), Function.identity()));
    }

    public void mergeAdditionalDetails( Project project , Project projectFromDb) {
        project.setAdditionalDetails(jsonMerge( objectMapper.valueToTree(projectFromDb.getAdditionalDetails()),
            objectMapper.valueToTree(project.getAdditionalDetails())));
    }
    /**
     * Method to merge additional details during update
     *
     * @param mainNode
     * @param updateNode
     * @return
     */
    public JsonNode jsonMerge(JsonNode mainNode, JsonNode updateNode) {

        if (isNull(mainNode) || mainNode.isNull())
            return updateNode;
        if (isNull(updateNode) || updateNode.isNull())
            return mainNode;

        Iterator<String> fieldNames = updateNode.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode jsonNode = mainNode.get(fieldName);
            // if field exists and is an embedded object
            if (jsonNode != null && jsonNode.isObject()) {
                jsonMerge(jsonNode, updateNode.get(fieldName));
            } else {
                if (mainNode instanceof ObjectNode) {
                    // Overwrite field
                    JsonNode value = updateNode.get(fieldName);
                    ((ObjectNode) mainNode).set(fieldName, value);
                }
            }

        }
        return mainNode;
    }
}
