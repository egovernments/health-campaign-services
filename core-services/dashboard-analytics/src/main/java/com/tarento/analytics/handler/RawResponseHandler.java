package com.tarento.analytics.handler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.analytics.constant.Constants;
import com.tarento.analytics.dto.AggregateDto;
import com.tarento.analytics.dto.AggregateRequestDto;
import com.tarento.analytics.enums.ChartType;
import com.tarento.analytics.utils.RawResponseTransformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RawResponseHandler implements IResponseHandler {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final ObjectMapper RAW_RESPONSE_MAPPER = new ObjectMapper()
			.configure(com.fasterxml.jackson.databind.DeserializationFeature.USE_LONG_FOR_INTS, true)
			.setSerializationInclusion(JsonInclude.Include.NON_NULL);

	@Autowired
	private RawResponseTransformer rawResponseTransformer;

	@Override
	public AggregateDto translate(AggregateRequestDto request, ObjectNode aggregations) throws IOException {
		JsonNode aggregationNode = aggregations.get(AGGREGATIONS);
		JsonNode chartNode = request.getChartNode();

		Map<String, Object> rawResponses = new LinkedHashMap<>();
		if (aggregationNode != null && aggregationNode.isObject()) {
			Iterator<Map.Entry<String, JsonNode>> fields = aggregationNode.fields();
			while (fields.hasNext()) {
				Map.Entry<String, JsonNode> entry = fields.next();
				rawResponses.put(entry.getKey(), RAW_RESPONSE_MAPPER.convertValue(entry.getValue(), Map.class));
			}
		}

		Map<String, String> transformDataTypes = buildTransformDataTypes(chartNode, aggregationNode);

		JsonNode transformNode = chartNode != null ? chartNode.get(Constants.JsonPaths.TRANSFORM) : null;
		Map<String, List<Map<String, String>>> transformationConfigs = new LinkedHashMap<>();
		Map<String, String> bucketsPaths = new LinkedHashMap<>();
		if (transformNode != null && transformNode.isObject()) {
			transformationConfigs.putAll(parseTransformationConfigs(transformNode));
			bucketsPaths.putAll(parseBucketsPaths(transformNode));
		}

		// Inline "select" mappings on queries (optional, overrides chartNode.transform for that key)
		transformationConfigs.putAll(parseInlineSelectMappings(chartNode));
		bucketsPaths.putAll(buildInlineBucketsPaths(chartNode, aggregationNode));

		Map<String, Object> transformed = transformationConfigs.isEmpty()
				? rawResponses
				: rawResponseTransformer.transformAll(rawResponses, transformationConfigs, transformDataTypes, bucketsPaths);

		List<String> aggregationPaths = parseAggregationPaths(chartNode);
		JsonNode mergesNode = chartNode != null ? chartNode.get(Constants.JsonPaths.MERGES) : null;
		if (mergesNode != null && mergesNode.isArray() && !mergesNode.isEmpty()) {
			rawResponseTransformer.executeMergeSteps(transformed, mergesNode, aggregationPaths);
		} else if (!aggregationPaths.isEmpty()) {
			Map<String, Object> ordered = new LinkedHashMap<>();
			for (String path : aggregationPaths) {
				if (transformed.containsKey(path)) {
					ordered.put(path, transformed.get(path));
				}
			}
			transformed.clear();
			transformed.putAll(ordered);
		}

		AggregateDto dto = new AggregateDto();
		dto.setChartType(ChartType.RAW_RESPONSE);
		dto.setVisualizationCode(request.getVisualizationCode());

		Map<String, Object> customData = new LinkedHashMap<>();
		customData.put("rawResponse", transformed);

		List<Map<String, Object>> cardsList = buildCardsList(chartNode, transformed);
		if (!cardsList.isEmpty()) {
			customData.put("cardsList", cardsList);
		}
		dto.setCustomData(customData);
		return dto;
	}

	private List<Map<String, Object>> buildCardsList(JsonNode chartNode, Map<String, Object> transformed) {
		if (chartNode == null || transformed == null || !chartNode.has("cardsList") || !chartNode.get("cardsList").isArray()) {
			return Collections.emptyList();
		}
		ArrayNode cardsNode = (ArrayNode) chartNode.get("cardsList");
		List<Map<String, Object>> out = new ArrayList<>(cardsNode.size());

		for (JsonNode c : cardsNode) {
			if (c == null || !c.isObject()) continue;
			String id = c.hasNonNull("id") ? c.get("id").asText() : null;
			String labelKey = c.hasNonNull("labelKey") ? c.get("labelKey").asText() : null;
			String valuePath = c.hasNonNull("valuePath") ? c.get("valuePath").asText() : null;
			String valueType = c.hasNonNull("valueType") ? c.get("valueType").asText() : null;

			Object value = valuePath != null ? transformed.get(valuePath) : null;

			Map<String, Object> card = new LinkedHashMap<>();
			if (id != null) card.put("id", id);
			if (labelKey != null) card.put("labelKey", labelKey);
			card.put("value", value);
			if (valueType != null) card.put("valueType", valueType);

			Map<String, Object> descParams = buildDescriptionParams(c.get("descriptionParams"), transformed);
			if (!descParams.isEmpty()) {
				card.put("descriptionParams", descParams);
			}

			String color = evaluateColor(c.get("colorRules"), transformed, value);
			if (color != null) {
				card.put("color", color);
			}
			out.add(card);
		}
		return out;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> buildDescriptionParams(JsonNode paramsNode, Map<String, Object> transformed) {
		if (paramsNode == null || !paramsNode.isObject() || transformed == null) return Collections.emptyMap();
		Map<String, Object> out = new LinkedHashMap<>();
		Iterator<Map.Entry<String, JsonNode>> fields = paramsNode.fields();
		while (fields.hasNext()) {
			Map.Entry<String, JsonNode> e = fields.next();
			String paramKey = e.getKey();
			JsonNode v = e.getValue();
			if (v == null || v.isNull()) {
				out.put(paramKey, null);
				continue;
			}
			if (v.isTextual()) {
				out.put(paramKey, transformed.get(v.asText()));
			} else if (v.isNumber()) {
				out.put(paramKey, v.numberValue());
			} else if (v.isBoolean()) {
				out.put(paramKey, v.booleanValue());
			} else if (v.isObject() || v.isArray()) {
				out.put(paramKey, RAW_RESPONSE_MAPPER.convertValue(v, Object.class));
			} else {
				out.put(paramKey, v.asText());
			}
		}
		return out;
	}

	private String evaluateColor(JsonNode colorRulesNode, Map<String, Object> transformed, Object fallbackValue) {
		if (colorRulesNode == null || colorRulesNode.isNull() || !colorRulesNode.isObject()) return null;
		String type = colorRulesNode.hasNonNull("type") ? colorRulesNode.get("type").asText() : null;
		if (Objects.equals(type, "constant")) {
			return colorRulesNode.hasNonNull("color") ? colorRulesNode.get("color").asText() : null;
		}
		if (!Objects.equals(type, "range")) return null;

		Object valObj = fallbackValue;
		if (colorRulesNode.hasNonNull("valuePath") && transformed != null) {
			valObj = transformed.get(colorRulesNode.get("valuePath").asText());
		}
		Double value = toDouble(valObj);

		JsonNode rangesNode = colorRulesNode.get("ranges");
		if (rangesNode != null && rangesNode.isArray()) {
			for (JsonNode r : rangesNode) {
				if (r == null || !r.isObject() || !r.hasNonNull("color")) continue;
				if (matchesRange(r, value)) {
					return r.get("color").asText();
				}
			}
		}
		// No default/fallback color from backend; UI can decide a fallback
		return null;
	}

	private boolean matchesRange(JsonNode rangeNode, Double value) {
		if (value == null) return false;
		if (rangeNode.hasNonNull("lte") && value > rangeNode.get("lte").asDouble()) return false;
		if (rangeNode.hasNonNull("lt") && value >= rangeNode.get("lt").asDouble()) return false;
		if (rangeNode.hasNonNull("gte") && value < rangeNode.get("gte").asDouble()) return false;
		if (rangeNode.hasNonNull("gt") && value <= rangeNode.get("gt").asDouble()) return false;
		return true;
	}

	private Double toDouble(Object val) {
		if (val == null) return null;
		if (val instanceof Number) return ((Number) val).doubleValue();
		try {
			return Double.parseDouble(String.valueOf(val));
		} catch (Exception e) {
			return null;
		}
	}

	private Map<String, String> buildTransformDataTypes(JsonNode chartNode, JsonNode aggregationNode) {
		Map<String, String> transformDataTypes = new LinkedHashMap<>();
		if (chartNode == null || chartNode.get(Constants.JsonPaths.QUERIES) == null || !chartNode.get(Constants.JsonPaths.QUERIES).isArray()
				|| aggregationNode == null || !aggregationNode.isObject()) {
			return transformDataTypes;
		}

		// Build a base mapping (queryKey -> transformData)
		Map<String, String> base = new LinkedHashMap<>();
		for (JsonNode q : chartNode.get(Constants.JsonPaths.QUERIES)) {
			if (q == null || !q.isObject() || !q.hasNonNull(Constants.JsonPaths.INDEX_NAME)) continue;
			String queryKey = q.hasNonNull("key")
					? q.get("key").asText()
					: (q.hasNonNull(Constants.JsonPaths.TRANSFORM_KEY)
						? q.get(Constants.JsonPaths.TRANSFORM_KEY).asText()
						: q.get(Constants.JsonPaths.INDEX_NAME).asText());
			String transformData = q.hasNonNull(Constants.JsonPaths.TRANSFORM_DATA)
					? q.get(Constants.JsonPaths.TRANSFORM_DATA).asText()
					: "rawDocuments";
			base.put(queryKey, transformData);
		}

		// Expand mapping to the actual response keys present in aggregationNode (handles auto-suffixed keys)
		Iterator<Map.Entry<String, JsonNode>> fields = aggregationNode.fields();
		while (fields.hasNext()) {
			String responseKey = fields.next().getKey();
			String baseKey = responseKey.contains("_") ? responseKey.substring(0, responseKey.lastIndexOf('_')) : responseKey;
			String t = base.containsKey(responseKey) ? base.get(responseKey) : base.get(baseKey);
			if (t != null) {
				transformDataTypes.put(responseKey, t);
			}
		}

		return transformDataTypes;
	}

	@SuppressWarnings("unchecked")
	private Map<String, List<Map<String, String>>> parseTransformationConfigs(JsonNode transformNode) {
		Map<String, List<Map<String, String>>> configs = new LinkedHashMap<>();
		Iterator<Map.Entry<String, JsonNode>> fields = transformNode.fields();
		while (fields.hasNext()) {
			Map.Entry<String, JsonNode> field = fields.next();
			String datasetKey = field.getKey();
			JsonNode indexConfig = field.getValue();
			JsonNode mappingsNode = indexConfig.get(Constants.JsonPaths.TRANSFORMATION_MAPPINGS);
			if (mappingsNode != null && mappingsNode.isArray()) {
				List<Map<String, String>> mappings = new ArrayList<>();
				for (JsonNode mappingNode : mappingsNode) {
					Map<String, String> mapping = MAPPER.convertValue(mappingNode, Map.class);
					mappings.add(mapping);
				}
				configs.put(datasetKey, mappings);
			}
		}
		return configs;
	}

	private Map<String, String> parseBucketsPaths(JsonNode transformNode) {
		Map<String, String> paths = new LinkedHashMap<>();
		Iterator<Map.Entry<String, JsonNode>> fields = transformNode.fields();
		while (fields.hasNext()) {
			Map.Entry<String, JsonNode> field = fields.next();
			String key = field.getKey();
			JsonNode config = field.getValue();
			if (config.has(Constants.JsonPaths.BUCKETS_PATH)) {
				paths.put(key, config.get(Constants.JsonPaths.BUCKETS_PATH).asText());
			}
		}
		return paths;
	}

	private List<String> parseAggregationPaths(JsonNode chartNode) {
		List<String> aggregationPaths = new ArrayList<>();
		if (chartNode == null) return aggregationPaths;
		JsonNode aggPathsNode = chartNode.get(Constants.JsonPaths.AGGREGATION_PATHS);
		if (aggPathsNode != null && aggPathsNode.isArray()) {
			for (JsonNode pathNode : aggPathsNode) {
				aggregationPaths.add(pathNode.asText());
			}
		}
		return aggregationPaths;
	}

	private Map<String, List<Map<String, String>>> parseInlineSelectMappings(JsonNode chartNode) {
		Map<String, List<Map<String, String>>> configs = new LinkedHashMap<>();
		if (chartNode == null || chartNode.get(Constants.JsonPaths.QUERIES) == null || !chartNode.get(Constants.JsonPaths.QUERIES).isArray()) {
			return configs;
		}

		for (JsonNode q : chartNode.get(Constants.JsonPaths.QUERIES)) {
			if (q == null || !q.isObject()) continue;
			if (!q.has("select") || !q.get("select").isObject()) {
				continue;
			}

			if (!q.hasNonNull(Constants.JsonPaths.INDEX_NAME)) continue;
			String key = q.hasNonNull("key")
					? q.get("key").asText()
					: (q.hasNonNull(Constants.JsonPaths.TRANSFORM_KEY)
						? q.get(Constants.JsonPaths.TRANSFORM_KEY).asText()
						: q.get(Constants.JsonPaths.INDEX_NAME).asText());

			@SuppressWarnings("unchecked")
			Map<String, String> mapping = MAPPER.convertValue(q.get("select"), Map.class);
			configs.put(key, Collections.singletonList(mapping));
		}

		return configs;
	}

	private Map<String, String> buildInlineBucketsPaths(JsonNode chartNode, JsonNode aggregationNode) {
		Map<String, String> paths = new LinkedHashMap<>();
		if (chartNode == null || !chartNode.has(Constants.JsonPaths.QUERIES) || !chartNode.get(Constants.JsonPaths.QUERIES).isArray()
				|| aggregationNode == null || !aggregationNode.isObject()) {
			return paths;
		}

		Map<String, String> base = new LinkedHashMap<>();
		ArrayNode queries = (ArrayNode) chartNode.get(Constants.JsonPaths.QUERIES);
		for (JsonNode q : queries) {
			if (q == null || !q.isObject() || !q.hasNonNull(Constants.JsonPaths.BUCKETS_PATH) || !q.hasNonNull(Constants.JsonPaths.INDEX_NAME)) continue;
			String key = q.hasNonNull("key")
					? q.get("key").asText()
					: (q.hasNonNull(Constants.JsonPaths.TRANSFORM_KEY)
						? q.get(Constants.JsonPaths.TRANSFORM_KEY).asText()
						: q.get(Constants.JsonPaths.INDEX_NAME).asText());
			base.put(key, q.get(Constants.JsonPaths.BUCKETS_PATH).asText());
		}

		Iterator<Map.Entry<String, JsonNode>> fields = aggregationNode.fields();
		while (fields.hasNext()) {
			String responseKey = fields.next().getKey();
			String baseKey = responseKey.contains("_") ? responseKey.substring(0, responseKey.lastIndexOf('_')) : responseKey;
			String p = base.containsKey(responseKey) ? base.get(responseKey) : base.get(baseKey);
			if (p != null) {
				paths.put(responseKey, p);
			}
		}

		return paths;
	}
}
