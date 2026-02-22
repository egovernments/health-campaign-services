package digit.service;

import digit.web.models.IndexField;
import digit.web.models.IndexSchema;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SchemaService {

    private final Map<String, IndexSchema> schemaRegistry = new HashMap<>();

    @PostConstruct
    public void initializeSchemas() {
        schemaRegistry.put("oy-project-task-index-v1", buildProjectTaskSchema());
        log.info("Initialized {} index schemas: {}", schemaRegistry.size(), schemaRegistry.keySet());
    }

    public IndexSchema getSchema(String indexName) {
        return schemaRegistry.get(indexName);
    }

    public boolean hasSchema(String indexName) {
        return schemaRegistry.containsKey(indexName);
    }

    /**
     * Formats the schema for inclusion in an LLM prompt.
     */
    public String formatSchemaForPrompt(IndexSchema schema) {
        StringBuilder sb = new StringBuilder();
        sb.append("Index: ").append(schema.getIndexName()).append("\n");
        sb.append("Fields:\n");
        for (IndexField field : schema.getFields()) {
            sb.append("    ").append(field.getName()).append(" (").append(field.getType()).append(")");
            if (field.getEnumValues() != null && !field.getEnumValues().isEmpty()) {
                sb.append(": [").append(String.join(", ", field.getEnumValues())).append("]");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private IndexSchema buildProjectTaskSchema() {
        List<IndexField> fields = new ArrayList<>();

        // Core fields
        fields.add(field("@timestamp", "date"));
        fields.add(field("id", "keyword"));
        fields.add(field("taskId", "keyword"));
        fields.add(field("taskClientReferenceId", "keyword"));
        fields.add(field("clientReferenceId", "keyword"));
        fields.add(field("individualId", "keyword"));
        fields.add(field("projectId", "keyword"));
        fields.add(field("projectType", "keyword"));
        fields.add(field("projectTypeId", "keyword"));
        fields.add(field("tenantId", "keyword"));

        // User fields
        fields.add(field("nameOfUser", "text"));
        fields.add(field("userName", "text"));
        fields.add(field("role", "keyword"));
        fields.add(field("gender", "keyword"));
        fields.add(field("age", "long"));
        fields.add(field("dateOfBirth", "long"));

        // Status fields
        fields.add(IndexField.builder()
                .name("status").type("keyword")
                .enumValues(List.of(
                        "ADMINISTRATION_FAILED", "ADMINISTRATION_SUCCESS",
                        "BENEFICIARY_REFUSED", "CLOSED_HOUSEHOLD",
                        "DELIVERED", "NOT_ADMINISTERED", "INELIGIBLE"
                )).build());
        fields.add(field("administrationStatus", "keyword"));
        fields.add(field("taskType", "keyword"));
        fields.add(field("isDelivered", "boolean"));
        fields.add(field("deliveredTo", "text"));
        fields.add(field("deliveryComments", "text"));

        // Product fields
        fields.add(field("productName", "keyword"));
        fields.add(field("productVariant", "keyword"));
        fields.add(field("quantity", "long"));

        // Audit fields
        fields.add(field("createdBy", "keyword"));
        fields.add(field("createdTime", "long"));
        fields.add(field("lastModifiedBy", "keyword"));
        fields.add(field("lastModifiedTime", "long"));
        fields.add(field("syncedDate", "date"));
        fields.add(field("syncedTime", "long"));
        fields.add(field("syncedTimeStamp", "date"));
        fields.add(field("taskDates", "date"));

        // Geo fields
        fields.add(field("latitude", "float"));
        fields.add(field("longitude", "float"));
        fields.add(field("geoPoint", "geo_point"));
        fields.add(field("locationAccuracy", "float"));
        fields.add(field("previousGeoPoint", "float"));
        fields.add(field("localityCode", "keyword"));

        // Boundary hierarchy
        fields.add(field("boundaryHierarchy.country", "keyword"));
        fields.add(field("boundaryHierarchy.state", "keyword"));
        fields.add(field("boundaryHierarchy.lga", "keyword"));
        fields.add(field("boundaryHierarchy.ward", "keyword"));
        fields.add(field("boundaryHierarchy.community", "keyword"));
        fields.add(field("boundaryHierarchy.healthFacility", "keyword"));

        // Boundary hierarchy codes
        fields.add(field("boundaryHierarchyCode.country", "keyword"));
        fields.add(field("boundaryHierarchyCode.state", "keyword"));
        fields.add(field("boundaryHierarchyCode.lga", "keyword"));
        fields.add(field("boundaryHierarchyCode.ward", "keyword"));
        fields.add(field("boundaryHierarchyCode.community", "keyword"));
        fields.add(field("boundaryHierarchyCode.healthFacility", "keyword"));

        // Additional details
        fields.add(field("additionalDetails.name", "text"));
        fields.add(field("additionalDetails.age", "keyword"));
        fields.add(field("additionalDetails.gender", "keyword"));
        fields.add(field("additionalDetails.uniqueBeneficiaryId", "keyword"));
        fields.add(field("additionalDetails.individualClientReferenceId", "keyword"));
        fields.add(field("additionalDetails.deliveryStrategy", "keyword"));
        fields.add(field("additionalDetails.deliveryType", "keyword"));
        fields.add(field("additionalDetails.doseIndex", "keyword"));
        fields.add(field("additionalDetails.cycleIndex", "keyword"));
        fields.add(field("additionalDetails.taskStatus", "keyword"));
        fields.add(field("additionalDetails.reAdministered", "keyword"));
        fields.add(field("additionalDetails.ineligibleReasons", "keyword"));
        fields.add(field("additionalDetails.latitude", "keyword"));
        fields.add(field("additionalDetails.longitude", "keyword"));
        fields.add(field("additionalDetails.dateOfAdministration", "keyword"));
        fields.add(field("additionalDetails.dateOfDelivery", "keyword"));
        fields.add(field("additionalDetails.dateOfVerification", "keyword"));

        return IndexSchema.builder()
                .indexName("oy-project-task-index-v1")
                .fields(fields)
                .build();
    }

    private IndexField field(String name, String type) {
        return IndexField.builder().name(name).type(type).build();
    }
}
