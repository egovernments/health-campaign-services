package org.egov.excelingestion.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.egov.excelingestion.web.models.excel.MultiSelectDetails;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for creating ColumnDef objects from JSON schema nodes
 * Ensures consistency across all generators
 */
@Component
public class ColumnDefMaker {

    /**
     * Create ColumnDef from JSON node with proper null handling for constraints
     */
    public ColumnDef createColumnDefFromJson(JsonNode node, String type) {
        ColumnDef.ColumnDefBuilder builder = ColumnDef.builder()
                .name(node.path("name").asText())
                .type(type)
                .description(node.path("description").asText())
                .colorHex(node.path("color").asText())
                .orderNumber(node.path("orderNumber").asInt(9999))
                .freezeColumnIfFilled(node.path("freezeColumnIfFilled").asBoolean(false))
                .hideColumn(node.path("hideColumn").asBoolean(false))
                .required(node.path("isRequired").asBoolean(false))
                .pattern(node.path("pattern").asText(null))
                .freezeColumn(node.path("freezeColumn").asBoolean(false))
                .adjustHeight(node.path("adjustHeight").asBoolean(false))
                .width(node.has("width") ? node.path("width").asInt() : 50)
                .wrapText(node.path("wrapText").asBoolean(false))
                .prefix(node.path("prefix").asText(null))
                .showInProcessed(node.path("showInProcessed").asBoolean(true))
                .unFreezeColumnTillData(node.path("unFreezeColumnTillData").asBoolean(false))
                .freezeTillData(node.path("freezeTillData").asBoolean(false));

        // Handle number constraints with proper null checking
        if (node.has("minimum")) {
            builder.minimum(node.path("minimum").asDouble());
        }
        if (node.has("maximum")) {
            builder.maximum(node.path("maximum").asDouble());
        }
        if (node.has("multipleOf")) {
            builder.multipleOf(node.path("multipleOf").asDouble());
        }
        if (node.has("exclusiveMinimum")) {
            builder.exclusiveMinimum(node.path("exclusiveMinimum").asDouble());
        }
        if (node.has("exclusiveMaximum")) {
            builder.exclusiveMaximum(node.path("exclusiveMaximum").asDouble());
        }

        // Handle string constraints with proper null checking
        if (node.has("minLength")) {
            builder.minLength(node.path("minLength").asInt());
        }
        if (node.has("maxLength")) {
            builder.maxLength(node.path("maxLength").asInt());
        }

        // Extract custom error message if provided in MDMS schema
        if (node.has("errorMessage")) {
            builder.errorMessage(node.path("errorMessage").asText());
        }

        // Handle enum values - support both "enumValues" and "enum" properties
        List<String> enumValues = null;
        if ("enum".equals(type)) {
            if (node.has("enumValues")) {
                enumValues = new ArrayList<>();
                for (JsonNode enumValue : node.path("enumValues")) {
                    enumValues.add(enumValue.asText());
                }
            } else if (node.has("enum")) {
                enumValues = new ArrayList<>();
                List<String> finalEnumValues = enumValues;
                node.path("enum").forEach(enumNode -> finalEnumValues.add(enumNode.asText()));
            }
            if (enumValues != null) {
                builder.enumValues(enumValues);
            }
        }

        // Handle multi-select details - support both "enumValues" and "enum"
        if (node.has("multiSelectDetails")) {
            JsonNode multiSelectNode = node.get("multiSelectDetails");
            List<String> multiSelectEnumValues = new ArrayList<>();
            if (multiSelectNode.has("enumValues")) {
                for (JsonNode enumValue : multiSelectNode.path("enumValues")) {
                    multiSelectEnumValues.add(enumValue.asText());
                }
            } else if (multiSelectNode.has("enum")) {
                List<String> finalMultiSelectEnumValues = multiSelectEnumValues;
                multiSelectNode.path("enum").forEach(enumNode -> finalMultiSelectEnumValues.add(enumNode.asText()));
            }
            MultiSelectDetails multiSelectDetails = MultiSelectDetails.builder()
                    .maxSelections(multiSelectNode.path("maxSelections").asInt(1))
                    .minSelections(multiSelectNode.path("minSelections").asInt(0))
                    .enumValues(!multiSelectEnumValues.isEmpty() ? multiSelectEnumValues : enumValues)
                    .build();
            builder.multiSelectDetails(multiSelectDetails);
        }

        return builder.build();
    }
}