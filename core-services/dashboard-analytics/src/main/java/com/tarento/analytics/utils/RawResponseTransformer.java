package com.tarento.analytics.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.tarento.analytics.constant.Constants;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transforms raw Elasticsearch responses (hits.hits[]._source) into flat, linear
 * structures using configurable transformation mappings per index.
 *
 * Supports:
 * - Nested field paths: Data.taskId
 * - Array index access: Data.geoPoint[0], Data.items[2].name
 * - Literal strings: 'value' or "value"
 * - Numeric literals: 42, 3.14
 * - Ternary expressions: condition ? trueExpr : falseExpr
 * - Logical OR fallback: expr || fallback
 * - Logical AND: expr1 && expr2
 * - String templates: `Delivered ${Data.quantity} ${Data.productName}`
 * - Equality checks: field === 'value', field !== 'value'
 * - Comparison operators: field > value, field < value, field >= value, field <= value
 *
 * Built-in functions:
 * - first(Data.items)               → first element of a list
 * - last(Data.items)                → last element of a list
 * - size(Data.items)                → size of a list
 * - formatDate(Data.timestamp, 'dd-MM-yyyy HH:mm:ss')  → format epoch millis or ISO date
 * - toUpperCase(Data.name)          → uppercase string
 * - toLowerCase(Data.name)          → lowercase string
 * - toString(Data.count)            → convert to string
 * - toNumber(Data.value)            → convert to number
 * - concat(Data.first, ' ', Data.last)  → concatenate multiple values
 * - substr(Data.name, 0, 5)         → substring
 * - replace(Data.text, 'old', 'new')   → string replace
 * - coalesce(Data.a, Data.b, 'default') → first non-null value
 * - join(Data.tags, ', ')           → join list elements with separator
 */
@Component
public class RawResponseTransformer {

    private static final Logger logger = LoggerFactory.getLogger(RawResponseTransformer.class);
    private static final Pattern TEMPLATE_VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern LITERAL_PATTERN = Pattern.compile("^'([^']*)'$");
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    private static final Pattern FUNCTION_PATTERN = Pattern.compile("^(\\w+)\\((.*)\\)$", Pattern.DOTALL);
    private static final Pattern ARRAY_INDEX_PATTERN = Pattern.compile("^([^\\[]+)\\[(\\d+)]$");

    public List<Map<String, Object>> transform(Map<String, Object> esResponse, List<Map<String, String>> mappings) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (esResponse == null || mappings == null || mappings.isEmpty()) {
            return results;
        }

        List<Map<String, Object>> sources = extractSources(esResponse);

        for (Map<String, String> mapping : mappings) {
            for (Map<String, Object> source : sources) {
                Map<String, Object> transformed = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : mapping.entrySet()) {
                    String targetField = entry.getKey();
                    String expression = entry.getValue();
                    Object value = evaluateExpression(expression, source);
                    transformed.put(targetField, value);
                }
                results.add(transformed);
            }
        }

        return results;
    }

    public Map<String, Object> transformAll(Map<String, Object> rawResponses,
                                             Map<String, List<Map<String, String>>> transformationConfigs,
                                             Map<String, String> transformDataTypes,
                                             Map<String, String> bucketsPaths) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawResponses.entrySet()) {
            String key = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> esResponse = (Map<String, Object>) entry.getValue();
            String baseKey = key.contains("_") ? key.substring(0, key.lastIndexOf('_')) : key;

            List<Map<String, String>> mappings = transformationConfigs.get(key);
            if (mappings == null) {
                mappings = transformationConfigs.get(baseKey);
            }

            if (mappings == null) {
                result.put(key, esResponse);
                continue;
            }

            String transformData = transformDataTypes != null ? transformDataTypes.getOrDefault(key, "rawDocuments") : "rawDocuments";

            switch (transformData) {
                case "linearAggregation":
                    Map<String, Object> linearFields = transformLinearAggregation(esResponse, mappings);
                    // Place linear aggregation fields directly into the result (flat)
                    // instead of nesting under the transformer key
                    result.putAll(linearFields);
                    break;
                case "termsAggregation":
                    String bucketsPath = bucketsPaths != null ? bucketsPaths.getOrDefault(key, null) : null;
                    if (bucketsPath == null) {
                        bucketsPath = bucketsPaths != null ? bucketsPaths.getOrDefault(baseKey, null) : null;
                    }
                    result.put(key, transformTermsAggregation(esResponse, mappings, bucketsPath));
                    break;
                case "rawDocuments":
                default:
                    result.put(key, transform(esResponse, mappings));
                    break;
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractSources(Map<String, Object> esResponse) {
        List<Map<String, Object>> sources = new ArrayList<>();
        Object hitsObj = esResponse.get("hits");
        if (!(hitsObj instanceof Map)) return sources;

        Map<String, Object> hitsOuter = (Map<String, Object>) hitsObj;
        Object hitsArray = hitsOuter.get("hits");
        if (!(hitsArray instanceof List)) return sources;

        for (Object hit : (List<?>) hitsArray) {
            if (hit instanceof Map) {
                Map<String, Object> hitMap = (Map<String, Object>) hit;
                Object source = hitMap.get("_source");
                if (source instanceof Map) {
                    sources.add((Map<String, Object>) source);
                }
            }
        }
        return sources;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractAggregations(Map<String, Object> esResponse) {
        Object aggObj = esResponse.get("aggregations");
        if (aggObj instanceof Map) {
            return (Map<String, Object>) aggObj;
        }
        return null;
    }

    public Map<String, Object> transformLinearAggregation(Map<String, Object> esResponse, List<Map<String, String>> mappings) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (esResponse == null || mappings == null || mappings.isEmpty()) {
            return result;
        }

        Map<String, Object> aggregations = extractAggregations(esResponse);
        if (aggregations == null) {
            logger.warn("No aggregations found in ES response for linearAggregation transform");
            return result;
        }

        for (Map<String, String> mapping : mappings) {
            for (Map.Entry<String, String> entry : mapping.entrySet()) {
                String targetField = entry.getKey();
                String expression = entry.getValue();
                Object value = evaluateExpression(expression, aggregations);
                result.put(targetField, value);
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> transformTermsAggregation(Map<String, Object> esResponse,
                                                                List<Map<String, String>> mappings,
                                                                String bucketsPath) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (esResponse == null || mappings == null || mappings.isEmpty() || bucketsPath == null) {
            logger.warn("Missing required parameters for termsAggregation transform (bucketsPath={})", bucketsPath);
            return results;
        }

        Map<String, Object> aggregations = extractAggregations(esResponse);
        if (aggregations == null) {
            logger.warn("No aggregations found in ES response for termsAggregation transform");
            return results;
        }

        // Navigate to the buckets list using the configured path
        Object bucketsObj = resolveField(bucketsPath, aggregations);
        if (!(bucketsObj instanceof List)) {
            logger.warn("bucketsPath '{}' did not resolve to a list", bucketsPath);
            return results;
        }

        List<?> buckets = (List<?>) bucketsObj;
        for (Object bucketObj : buckets) {
            if (!(bucketObj instanceof Map)) continue;
            Map<String, Object> bucket = (Map<String, Object>) bucketObj;

            for (Map<String, String> mapping : mappings) {
                Map<String, Object> transformed = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : mapping.entrySet()) {
                    String targetField = entry.getKey();
                    String expression = entry.getValue();
                    Object value = evaluateExpression(expression, bucket);
                    transformed.put(targetField, value);
                }
                results.add(transformed);
            }
        }

        return results;
    }

    // ---- Dataset merge/join ----

    /**
     * Executes an ordered array of merge steps. Each step joins exactly 2 datasets
     * and stores the result under its mergeKey. A step's baseDataset can reference
     * the mergeKey of a previous step, enabling chained merges:
     *
     *   step0: dataset1 + dataset2 → merge12
     *   step1: merge12  + dataset3 → merge123
     *   step2: merge123 + dataset4 → merge1234
     *
     * Each step may specify "clearDatasets" — a list of dataset names to remove
     * from memory after the step completes (for GC).
     *
     * Join types: "left" (default), "inner", "full"
     *
     * After all steps, only datasets listed in aggregationPaths are retained
     * in the response.
     */
    @SuppressWarnings("unchecked")
    public void executeMergeSteps(Map<String, Object> transformedData,
                                   JsonNode mergesConfig,
                                   List<String> aggregationPaths) {
        if (mergesConfig == null || !mergesConfig.isArray() || mergesConfig.size() == 0) {
            logger.warn("No merge steps configured");
            return;
        }

        for (int step = 0; step < mergesConfig.size(); step++) {
            JsonNode stepConfig = mergesConfig.get(step);

            String mergeKey = stepConfig.has(Constants.JsonPaths.MERGE_KEY)
                    ? stepConfig.get(Constants.JsonPaths.MERGE_KEY).asText() : "merged_" + step;

            String mergeType = stepConfig.has(Constants.JsonPaths.MERGE_TYPE)
                    ? stepConfig.get(Constants.JsonPaths.MERGE_TYPE).asText() : "join";

            // Linear merge: combine multiple single-object datasets into one flat result
            if ("linear".equals(mergeType)) {
                Map<String, Object> linearResult = performLinearMerge(transformedData, stepConfig, step);
                if (linearResult != null) {
                    boolean flatten = stepConfig.has("flatten") && stepConfig.get("flatten").asBoolean(false);
                    if (flatten) {
                        // Place all result fields directly into transformedData (rawResponse level)
                        transformedData.putAll(linearResult);
                    } else {
                        transformedData.put(mergeKey, linearResult);
                    }
                }

                // Free datasets listed in clearDatasets
                JsonNode clearNode = stepConfig.get(Constants.JsonPaths.CLEAR_DATASETS);
                if (clearNode != null && clearNode.isArray()) {
                    for (JsonNode nameNode : clearNode) {
                        transformedData.remove(nameNode.asText());
                    }
                }

                logger.debug("Linear merge step {}: → {} ", step, mergeKey);
                continue;
            }

            // Standard join merge
            String baseDatasetName = stepConfig.get(Constants.JsonPaths.BASE_DATASET).asText();
            String joinDatasetName = stepConfig.get(Constants.JsonPaths.JOIN_DATASET).asText();
            String baseKeyField = stepConfig.get(Constants.JsonPaths.BASE_KEY).asText();
            String joinKeyField = stepConfig.get(Constants.JsonPaths.JOIN_KEY).asText();
            String joinType = stepConfig.has(Constants.JsonPaths.JOIN_TYPE)
                    ? stepConfig.get(Constants.JsonPaths.JOIN_TYPE).asText() : "left";

            Object baseObj = transformedData.get(baseDatasetName);
            if (!(baseObj instanceof List)) {
                logger.warn("Merge step {}: base dataset '{}' not found or not a list, skipping", step, baseDatasetName);
                continue;
            }
            Object joinObj = transformedData.get(joinDatasetName);
            if (!(joinObj instanceof List)) {
                logger.warn("Merge step {}: join dataset '{}' not found or not a list, skipping", step, joinDatasetName);
                continue;
            }

            List<Map<String, Object>> baseDataset = (List<Map<String, Object>>) baseObj;
            List<Map<String, Object>> joinDataset = (List<Map<String, Object>>) joinObj;

            List<Map<String, Object>> merged;
            if ("concat".equals(joinType)) {
                merged = new ArrayList<>(baseDataset.size() + joinDataset.size());
                merged.addAll(baseDataset);
                merged.addAll(joinDataset);
            } else {
                // Parse optional outputMappings for this step
                List<Map<String, String>> outputMappings = parseOutputMappings(
                        stepConfig.get(Constants.JsonPaths.OUTPUT_MAPPINGS));

                // Perform the join
                merged = performJoin(
                        baseDataset, baseDatasetName,
                        joinDataset, joinDatasetName,
                        baseKeyField, joinKeyField, joinType,
                        outputMappings);
            }

            // Store result — available as baseDataset for subsequent steps
            transformedData.put(mergeKey, merged);

            // Free datasets listed in clearDatasets
            JsonNode clearNode = stepConfig.get(Constants.JsonPaths.CLEAR_DATASETS);
            if (clearNode != null && clearNode.isArray()) {
                for (JsonNode nameNode : clearNode) {
                    String name = nameNode.asText();
                    transformedData.remove(name);
                }
            }

            logger.debug("Merge step {}: {} + {} → {} ({} records)", step,
                    baseDatasetName, joinDatasetName, mergeKey, merged.size());
        }

        // Retain only datasets listed in aggregationPaths, ordered as specified
        if (aggregationPaths != null && !aggregationPaths.isEmpty()) {
            Map<String, Object> ordered = new LinkedHashMap<>();
            for (String path : aggregationPaths) {
                if (transformedData.containsKey(path)) {
                    ordered.put(path, transformedData.get(path));
                }
            }
            transformedData.clear();
            transformedData.putAll(ordered);
        }
    }

    /**
     * Performs a linear merge: combines multiple single-object (linear aggregation) datasets
     * into one flat result object. Supports outputMappings with expression evaluation
     * including arithmetic operations (add, subtract, multiply, divide), ternary, concat, etc.
     *
     * Each dataset referenced in the "datasets" array should be a Map (single object),
     * not a List. All fields from all datasets are gathered into a context keyed by
     * dataset name, and outputMappings are evaluated against that context.
     *
     * Without outputMappings, all fields from all datasets are merged flat into one object.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> performLinearMerge(Map<String, Object> transformedData,
                                                     JsonNode stepConfig, int step) {
        JsonNode datasetsNode = stepConfig.get(Constants.JsonPaths.LINEAR_MERGE_DATASETS);
        boolean hasDatasets = datasetsNode != null && datasetsNode.isArray() && !datasetsNode.isEmpty();

        // Build context for expression evaluation
        Map<String, Object> context = new LinkedHashMap<>();

        if (hasDatasets) {
            // Gather named datasets into context with prefixed + flat access
            for (JsonNode dsNode : datasetsNode) {
                String dsName = dsNode.asText();
                Object dsObj = transformedData.get(dsName);
                if (dsObj == null) {
                    logger.warn("Linear merge step {}: dataset '{}' not found, treating as empty", step, dsName);
                    context.put(dsName, Collections.emptyMap());
                    continue;
                }
                Map<String, Object> dsMap;
                if (dsObj instanceof Map) {
                    dsMap = (Map<String, Object>) dsObj;
                } else if (dsObj instanceof List) {
                    List<?> dsList = (List<?>) dsObj;
                    if (dsList.size() == 1 && dsList.get(0) instanceof Map) {
                        dsMap = (Map<String, Object>) dsList.get(0);
                    } else {
                        logger.warn("Linear merge step {}: dataset '{}' is a list with {} elements, expected single object, skipping",
                                step, dsName, dsList.size());
                        context.put(dsName, Collections.emptyMap());
                        continue;
                    }
                } else {
                    logger.warn("Linear merge step {}: dataset '{}' is not a Map or List, skipping", step, dsName);
                    context.put(dsName, Collections.emptyMap());
                    continue;
                }
                context.put(dsName, dsMap);
                context.putAll(dsMap);
            }
        } else {
            // No datasets specified — use all flat fields from transformedData directly.
            // This works when linearAggregation fields are already flat in transformedData.
            for (Map.Entry<String, Object> entry : transformedData.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof Map) {
                    // Named dataset — add both prefixed and flat access
                    context.put(entry.getKey(), val);
                    context.putAll((Map<String, Object>) val);
                } else if (!(val instanceof List)) {
                    // Scalar value (flat linear aggregation field) — add directly
                    context.put(entry.getKey(), val);
                }
            }
        }

        // Apply outputMappings if provided
        List<Map<String, String>> outputMappings = parseOutputMappings(
                stepConfig.get(Constants.JsonPaths.OUTPUT_MAPPINGS));

        Map<String, Object> result;
        if (outputMappings != null && !outputMappings.isEmpty()) {
            result = new LinkedHashMap<>();
            for (Map<String, String> mapping : outputMappings) {
                for (Map.Entry<String, String> entry : mapping.entrySet()) {
                    result.put(entry.getKey(), evaluateExpression(entry.getValue(), context));
                }
            }
        } else {
            // No mappings — just copy all context scalars
            result = new LinkedHashMap<>(context);
        }

        return result;
    }

    /**
     * Joins two datasets. Supports left, inner, and full join types.
     *
     * If outputMappings is provided, applies them to produce shaped output records
     * with dataset-prefixed field access (e.g. "usersInfo.userId", "syncStatus.active").
     *
     * If outputMappings is empty/null, produces flat merged records where all fields
     * from both sides are at the top level (base fields take priority on conflicts).
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> performJoin(
            List<Map<String, Object>> baseDataset, String baseName,
            List<Map<String, Object>> joinDataset, String joinName,
            String baseKeyField, String joinKeyField, String joinType,
            List<Map<String, String>> outputMappings) {

        // Build hash index on the join dataset for O(1) lookups
        Map<String, Map<String, Object>> joinIndex = new HashMap<>(joinDataset.size() * 4 / 3 + 1);
        for (Map<String, Object> record : joinDataset) {
            Object keyVal = record.get(joinKeyField);
            if (keyVal != null) {
                joinIndex.put(String.valueOf(keyVal), record);
            }
        }

        boolean isFullJoin = "full".equals(joinType);
        Set<String> matchedJoinKeys = isFullJoin ? new HashSet<>(joinIndex.size()) : null;
        boolean hasOutputMappings = outputMappings != null && !outputMappings.isEmpty();

        List<Map<String, Object>> results = new ArrayList<>();

        // Pass 1: iterate base records
        for (Map<String, Object> baseRecord : baseDataset) {
            Object baseKeyVal = baseRecord.get(baseKeyField);
            String keyStr = baseKeyVal != null ? String.valueOf(baseKeyVal) : null;
            Map<String, Object> joinedRecord = keyStr != null ? joinIndex.get(keyStr) : null;

            if (joinedRecord == null && "inner".equals(joinType)) {
                continue;
            }

            if (joinedRecord != null && matchedJoinKeys != null) {
                matchedJoinKeys.add(keyStr);
            }

            buildJoinedRecord(results, baseRecord, baseName, joinedRecord, joinName, hasOutputMappings, outputMappings);
        }

        // Pass 2 (full join only): add unmatched records from join dataset
        if (isFullJoin) {
            for (Map<String, Object> joinRecord : joinDataset) {
                Object keyVal = joinRecord.get(joinKeyField);
                String keyStr = keyVal != null ? String.valueOf(keyVal) : null;
                if (keyStr != null && !matchedJoinKeys.contains(keyStr)) {
                    buildJoinedRecord(results, null, baseName, joinRecord, joinName, hasOutputMappings, outputMappings);
                }
            }
            matchedJoinKeys.clear(); // help GC
        }

        joinIndex.clear(); // help GC
        return results;
    }

    /**
     * Builds output record(s) for a single joined pair and adds to results.
     *
     * With outputMappings: creates a nested context {baseName: baseRecord, joinName: joinRecord}
     * so expressions like "usersInfo.userId" resolve. Also exposes flat fields for non-prefixed access.
     *
     * Without outputMappings: flattens both records into one map (base fields win on conflict).
     */
    private void buildJoinedRecord(List<Map<String, Object>> results,
                                    Map<String, Object> baseRecord, String baseName,
                                    Map<String, Object> joinedRecord, String joinName,
                                    boolean hasOutputMappings,
                                    List<Map<String, String>> outputMappings) {
        if (hasOutputMappings) {
            Map<String, Object> context = new LinkedHashMap<>(4);
            Map<String, Object> safeBase = baseRecord != null ? baseRecord : Collections.emptyMap();
            Map<String, Object> safeJoin = joinedRecord != null ? joinedRecord : Collections.emptyMap();
            context.put(baseName, safeBase);
            context.put(joinName, safeJoin);
            // Also expose flat fields for non-prefixed expressions
            context.putAll(safeJoin);
            context.putAll(safeBase); // base wins on conflict

            for (Map<String, String> mapping : outputMappings) {
                Map<String, Object> outputRecord = new LinkedHashMap<>(mapping.size() * 4 / 3 + 1);
                for (Map.Entry<String, String> entry : mapping.entrySet()) {
                    outputRecord.put(entry.getKey(), evaluateExpression(entry.getValue(), context));
                }
                results.add(outputRecord);
            }
        } else {
            // No output mappings — flatten both records for use as input to next step
            Map<String, Object> flat = new LinkedHashMap<>();
            if (baseRecord != null) flat.putAll(baseRecord);
            if (joinedRecord != null) {
                for (Map.Entry<String, Object> e : joinedRecord.entrySet()) {
                    flat.putIfAbsent(e.getKey(), e.getValue());
                }
            }
            results.add(flat);
        }
    }

    private List<Map<String, String>> parseOutputMappings(JsonNode outputMappingsNode) {
        if (outputMappingsNode == null || !outputMappingsNode.isArray()) {
            return Collections.emptyList();
        }
        List<Map<String, String>> outputMappings = new ArrayList<>(outputMappingsNode.size());
        for (JsonNode mappingNode : outputMappingsNode) {
            Map<String, String> mapping = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = mappingNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                mapping.put(field.getKey(), field.getValue().asText());
            }
            outputMappings.add(mapping);
        }
        return outputMappings;
    }

    Object evaluateExpression(String expression, Map<String, Object> source) {
        if (expression == null) return null;
        expression = expression.trim();

        // Literal string: 'value'
        Matcher literalMatcher = LITERAL_PATTERN.matcher(expression);
        if (literalMatcher.matches()) {
            return literalMatcher.group(1);
        }

        // Empty string literal
        if (expression.equals("''") || expression.equals("\"\"")) {
            return "";
        }

        // Numeric literal: 42, 3.14, -1
        if (NUMERIC_PATTERN.matcher(expression).matches()) {
            if (expression.contains(".")) {
                return Double.parseDouble(expression);
            }
            return Long.parseLong(expression);
        }

        // Boolean literals
        if (expression.equals("true")) return Boolean.TRUE;
        if (expression.equals("false")) return Boolean.FALSE;
        if (expression.equals("null")) return null;

        // Ternary: condition ? trueExpr : falseExpr
        int ternaryIdx = findTernaryOperator(expression);
        if (ternaryIdx > 0) {
            String condition = expression.substring(0, ternaryIdx).trim();
            String rest = expression.substring(ternaryIdx + 1).trim();
            int colonIdx = findTernaryColon(rest);
            if (colonIdx > 0) {
                String trueExpr = rest.substring(0, colonIdx).trim();
                String falseExpr = rest.substring(colonIdx + 1).trim();
                boolean condResult = evaluateCondition(condition, source);
                return condResult ? evaluateExpression(trueExpr, source) : evaluateExpression(falseExpr, source);
            }
        }

        // Logical OR: expr || fallback
        int orIdx = findOperatorOutsideFunctions(expression, "||");
        if (orIdx > 0) {
            String left = expression.substring(0, orIdx).trim();
            String right = expression.substring(orIdx + 2).trim();
            Object leftVal = evaluateExpression(left, source);
            if (isTruthy(leftVal)) return leftVal;
            return evaluateExpression(right, source);
        }

        // String template: `text ${field} text`
        if (expression.startsWith("`") && expression.endsWith("`")) {
            String template = expression.substring(1, expression.length() - 1);
            return evaluateTemplate(template, source);
        }

        // Function call: fnName(arg1, arg2, ...)
        Matcher fnMatcher = FUNCTION_PATTERN.matcher(expression);
        if (fnMatcher.matches()) {
            String fnName = fnMatcher.group(1);
            String argsStr = fnMatcher.group(2).trim();
            return evaluateFunction(fnName, argsStr, source);
        }

        // Simple nested field path (with optional array index)
        return resolveField(expression, source);
    }

    // ---- Function evaluation ----

    private Object evaluateFunction(String fnName, String argsStr, Map<String, Object> source) {
        List<String> args = splitFunctionArgs(argsStr);

        switch (fnName) {
            case "first":
                return fnFirst(args, source);
            case "last":
                return fnLast(args, source);
            case "size":
                return fnSize(args, source);
            case "formatDate":
                return fnFormatDate(args, source);
            case "toUpperCase":
                return fnToUpperCase(args, source);
            case "toLowerCase":
                return fnToLowerCase(args, source);
            case "toString":
                return fnToString(args, source);
            case "toNumber":
                return fnToNumber(args, source);
            case "concat":
                return fnConcat(args, source);
            case "substr":
                return fnSubstr(args, source);
            case "replace":
                return fnReplace(args, source);
            case "coalesce":
                return fnCoalesce(args, source);
            case "join":
                return fnJoin(args, source);
            case "add":
            case "sum":
                return fnArithmetic(args, source, '+');
            case "subtract":
                return fnArithmetic(args, source, '-');
            case "multiply":
                return fnArithmetic(args, source, '*');
            case "divide":
                return fnArithmetic(args, source, '/');
            default:
                logger.warn("Unknown function: {}", fnName);
                return null;
        }
    }

    private Object fnFirst(List<String> args, Map<String, Object> source) {
        if (args.isEmpty()) return null;
        Object val = evaluateExpression(args.get(0), source);
        if (val instanceof List) {
            List<?> list = (List<?>) val;
            return list.isEmpty() ? null : list.get(0);
        }
        return val;
    }

    private Object fnLast(List<String> args, Map<String, Object> source) {
        if (args.isEmpty()) return null;
        Object val = evaluateExpression(args.get(0), source);
        if (val instanceof List) {
            List<?> list = (List<?>) val;
            return list.isEmpty() ? null : list.get(list.size() - 1);
        }
        return val;
    }

    private Object fnSize(List<String> args, Map<String, Object> source) {
        if (args.isEmpty()) return 0;
        Object val = evaluateExpression(args.get(0), source);
        if (val instanceof List) return ((List<?>) val).size();
        if (val instanceof Map) return ((Map<?, ?>) val).size();
        if (val instanceof String) return ((String) val).length();
        return val == null ? 0 : 1;
    }

    private static final DateTimeFormatter[] ISO_DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ZoneOffset.UTC),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX").withZone(ZoneOffset.UTC),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC),
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)
    };

    private Object fnFormatDate(List<String> args, Map<String, Object> source) {
        if (args.size() < 2) return null;
        Object val = evaluateExpression(args.get(0), source);
        Object patternObj = evaluateExpression(args.get(1), source);
        if (val == null || patternObj == null) return null;

        String outputPattern = String.valueOf(patternObj);
        try {
            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern(outputPattern).withZone(ZoneOffset.UTC);
            Instant instant = null;

            if (val instanceof Number) {
                long epochMillis = ((Number) val).longValue();
                if (epochMillis > 0 && epochMillis < 4102444800L) {
                    epochMillis = epochMillis * 1000;
                }
                instant = Instant.ofEpochMilli(epochMillis);
            } else {
                String valStr = String.valueOf(val).trim();
                try {
                    long epochMillis = Long.parseLong(valStr);
                    if (epochMillis > 0 && epochMillis < 4102444800L) {
                        epochMillis = epochMillis * 1000;
                    }
                    instant = Instant.ofEpochMilli(epochMillis);
                } catch (NumberFormatException e) {
                    instant = parseIsoDate(valStr);
                }
            }

            if (instant != null) {
                return outputFormatter.format(instant);
            }
            logger.warn("Could not parse date value: {}", val);
            return String.valueOf(val);
        } catch (Exception e) {
            logger.warn("Failed to format date: {}", e.getMessage());
            return String.valueOf(val);
        }
    }

    private Instant parseIsoDate(String dateStr) {
        for (DateTimeFormatter formatter : ISO_DATE_FORMATTERS) {
            try {
                return ZonedDateTime.parse(dateStr, formatter).toInstant();
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return null;
    }

    private Object fnToUpperCase(List<String> args, Map<String, Object> source) {
        if (args.isEmpty()) return null;
        Object val = evaluateExpression(args.get(0), source);
        return val != null ? String.valueOf(val).toUpperCase() : null;
    }

    private Object fnToLowerCase(List<String> args, Map<String, Object> source) {
        if (args.isEmpty()) return null;
        Object val = evaluateExpression(args.get(0), source);
        return val != null ? String.valueOf(val).toLowerCase() : null;
    }

    private Object fnToString(List<String> args, Map<String, Object> source) {
        if (args.isEmpty()) return null;
        Object val = evaluateExpression(args.get(0), source);
        return val != null ? String.valueOf(val) : null;
    }

    private Object fnToNumber(List<String> args, Map<String, Object> source) {
        if (args.isEmpty()) return null;
        Object val = evaluateExpression(args.get(0), source);
        if (val instanceof Number) return val;
        if (val == null) return null;
        String str = String.valueOf(val).trim();
        try {
            if (str.contains(".")) return Double.parseDouble(str);
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Object fnConcat(List<String> args, Map<String, Object> source) {
        StringBuilder sb = new StringBuilder();
        for (String arg : args) {
            Object val = evaluateExpression(arg, source);
            sb.append(val != null ? String.valueOf(val) : "");
        }
        return sb.toString();
    }

    private Object fnSubstr(List<String> args, Map<String, Object> source) {
        if (args.size() < 2) return null;
        Object val = evaluateExpression(args.get(0), source);
        if (val == null) return null;
        String str = String.valueOf(val);
        try {
            int start = ((Number) evaluateExpression(args.get(1), source)).intValue();
            if (args.size() >= 3) {
                int end = ((Number) evaluateExpression(args.get(2), source)).intValue();
                return str.substring(Math.min(start, str.length()), Math.min(end, str.length()));
            }
            return str.substring(Math.min(start, str.length()));
        } catch (Exception e) {
            return str;
        }
    }

    private Object fnReplace(List<String> args, Map<String, Object> source) {
        if (args.size() < 3) return null;
        Object val = evaluateExpression(args.get(0), source);
        if (val == null) return null;
        Object target = evaluateExpression(args.get(1), source);
        Object replacement = evaluateExpression(args.get(2), source);
        return String.valueOf(val).replace(
                String.valueOf(target),
                replacement != null ? String.valueOf(replacement) : ""
        );
    }

    private Object fnCoalesce(List<String> args, Map<String, Object> source) {
        for (String arg : args) {
            Object val = evaluateExpression(arg, source);
            if (val != null) return val;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object fnJoin(List<String> args, Map<String, Object> source) {
        if (args.isEmpty()) return null;
        Object val = evaluateExpression(args.get(0), source);
        String separator = args.size() >= 2 ? String.valueOf(evaluateExpression(args.get(1), source)) : ",";
        if (val instanceof List) {
            List<?> list = (List<?>) val;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(separator);
                sb.append(list.get(i) != null ? String.valueOf(list.get(i)) : "");
            }
            return sb.toString();
        }
        return val != null ? String.valueOf(val) : null;
    }

    private Object fnArithmetic(List<String> args, Map<String, Object> source, char op) {
        if (args.size() < 2) return null;
        Object leftVal = evaluateExpression(args.get(0), source);
        Object rightVal = evaluateExpression(args.get(1), source);
        double left = toDouble(leftVal);
        double right = toDouble(rightVal);
        double result;
        switch (op) {
            case '+': result = left + right; break;
            case '-': result = left - right; break;
            case '*': result = left * right; break;
            case '/': result = right != 0 ? left / right : 0; break;
            default: return null;
        }
        if (result == Math.floor(result) && !Double.isInfinite(result)) {
            return (long) result;
        }
        return result;
    }

    // ---- Condition evaluation ----

    private boolean evaluateCondition(String condition, Map<String, Object> source) {
        condition = condition.trim();

        // Logical AND: expr1 && expr2 (lowest precedence — check first)
        int andIdx = findOperatorOutsideFunctions(condition, "&&");
        if (andIdx > 0) {
            String left = condition.substring(0, andIdx).trim();
            String right = condition.substring(andIdx + 2).trim();
            return evaluateCondition(left, source) && evaluateCondition(right, source);
        }

        // Equality: field === 'value'
        int eqIdx = condition.indexOf("===");
        if (eqIdx > 0) {
            String left = condition.substring(0, eqIdx).trim();
            String right = condition.substring(eqIdx + 3).trim();
            Object leftVal = evaluateExpression(left, source);
            Object rightVal = evaluateExpression(right, source);
            return Objects.equals(String.valueOf(leftVal), String.valueOf(rightVal));
        }

        // Inequality: field !== 'value'
        int neqIdx = condition.indexOf("!==");
        if (neqIdx > 0) {
            String left = condition.substring(0, neqIdx).trim();
            String right = condition.substring(neqIdx + 3).trim();
            Object leftVal = evaluateExpression(left, source);
            Object rightVal = evaluateExpression(right, source);
            return !Objects.equals(String.valueOf(leftVal), String.valueOf(rightVal));
        }

        // Greater than or equal: field >= value
        int geIdx = condition.indexOf(">=");
        if (geIdx > 0) {
            return compareValues(condition, geIdx, 2, source) >= 0;
        }

        // Less than or equal: field <= value
        int leIdx = condition.indexOf("<=");
        if (leIdx > 0) {
            return compareValues(condition, leIdx, 2, source) <= 0;
        }

        // Greater than: field > value
        int gtIdx = findComparisonOperator(condition, '>');
        if (gtIdx > 0) {
            return compareValues(condition, gtIdx, 1, source) > 0;
        }

        // Less than: field < value
        int ltIdx = findComparisonOperator(condition, '<');
        if (ltIdx > 0) {
            return compareValues(condition, ltIdx, 1, source) < 0;
        }

        // Truthiness check
        Object val = evaluateExpression(condition, source);
        return isTruthy(val);
    }

    private int compareValues(String condition, int opIdx, int opLen, Map<String, Object> source) {
        String left = condition.substring(0, opIdx).trim();
        String right = condition.substring(opIdx + opLen).trim();
        Object leftVal = evaluateExpression(left, source);
        Object rightVal = evaluateExpression(right, source);
        double leftNum = toDouble(leftVal);
        double rightNum = toDouble(rightVal);
        return Double.compare(leftNum, rightNum);
    }

    private double toDouble(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val == null) return 0;
        try {
            return Double.parseDouble(String.valueOf(val));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int findComparisonOperator(String expr, char op) {
        // Find > or < that's not part of >= or <=
        for (int i = 1; i < expr.length(); i++) {
            if (expr.charAt(i) == op) {
                if (i + 1 < expr.length() && expr.charAt(i + 1) == '=') continue;
                if (i > 0 && expr.charAt(i - 1) == '!') continue;
                return i;
            }
        }
        return -1;
    }

    // ---- Template evaluation ----

    private String evaluateTemplate(String template, Map<String, Object> source) {
        Matcher matcher = TEMPLATE_VAR_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String expr = matcher.group(1).trim();
            Object val = evaluateExpression(expr, source);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(val != null ? String.valueOf(val) : ""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // ---- Field resolution with array index support ----

    @SuppressWarnings("unchecked")
    Object resolveField(String fieldPath, Map<String, Object> source) {
        if (fieldPath == null || source == null) return null;
        String[] parts = fieldPath.split("\\.");
        Object current = source;
        for (String part : parts) {
            if (current == null) return null;

            if (part.indexOf('[') >= 0) {
                // Check for array index: fieldName[0]
                Matcher arrMatcher = ARRAY_INDEX_PATTERN.matcher(part);
                if (arrMatcher.matches()) {
                    String fieldName = arrMatcher.group(1);
                    int index = Integer.parseInt(arrMatcher.group(2));
                    if (current instanceof Map) {
                        current = ((Map<String, Object>) current).get(fieldName);
                    } else {
                        return null;
                    }
                    if (current instanceof List) {
                        List<?> list = (List<?>) current;
                        current = index < list.size() ? list.get(index) : null;
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            } else {
                if (current instanceof Map) {
                    current = ((Map<String, Object>) current).get(part);
                } else {
                    return null;
                }
            }
        }
        return normalizeNumber(current);
    }

    private Object normalizeNumber(Object value) {
        if (value instanceof Double) {
            Double d = (Double) value;
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return d.longValue();
            }
        }
        return value;
    }

    // ---- Utility methods ----

    private boolean isTruthy(Object val) {
        if (val == null) return false;
        if (val instanceof String) return !((String) val).isEmpty();
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof Number) return ((Number) val).doubleValue() != 0;
        if (val instanceof List) return !((List<?>) val).isEmpty();
        return true;
    }

    private int findTernaryOperator(String expr) {
        int depth = 0;
        boolean inTemplate = false;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '`') inTemplate = !inTemplate;
            if (!inTemplate) {
                if (c == '(') depth++;
                else if (c == ')') depth--;
                else if (c == '?' && depth == 0 && i > 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int findTernaryColon(String expr) {
        int depth = 0;
        boolean inTemplate = false;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '`') inTemplate = !inTemplate;
            if (!inTemplate) {
                if (c == '(') depth++;
                else if (c == ')') depth--;
                else if (c == ':' && depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int findOperatorOutsideFunctions(String expr, String op) {
        int depth = 0;
        boolean inTemplate = false;
        boolean inSingleQuote = false;
        for (int i = 0; i < expr.length() - op.length() + 1; i++) {
            char c = expr.charAt(i);
            if (c == '\'' && !inTemplate) inSingleQuote = !inSingleQuote;
            if (c == '`' && !inSingleQuote) inTemplate = !inTemplate;
            if (!inTemplate && !inSingleQuote) {
                if (c == '(') depth++;
                else if (c == ')') depth--;
                else if (depth == 0 && expr.startsWith(op, i)) {
                    return i;
                }
            }
        }
        return -1;
    }

    List<String> splitFunctionArgs(String argsStr) {
        List<String> args = new ArrayList<>();
        if (argsStr == null || argsStr.trim().isEmpty()) return args;

        int depth = 0;
        boolean inSingleQuote = false;
        boolean inTemplate = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < argsStr.length(); i++) {
            char c = argsStr.charAt(i);

            if (c == '\'' && !inTemplate) {
                inSingleQuote = !inSingleQuote;
                current.append(c);
            } else if (c == '`' && !inSingleQuote) {
                inTemplate = !inTemplate;
                current.append(c);
            } else if (!inSingleQuote && !inTemplate) {
                if (c == '(') {
                    depth++;
                    current.append(c);
                } else if (c == ')') {
                    depth--;
                    current.append(c);
                } else if (c == ',' && depth == 0) {
                    args.add(current.toString().trim());
                    current = new StringBuilder();
                } else {
                    current.append(c);
                }
            } else {
                current.append(c);
            }
        }

        String last = current.toString().trim();
        if (!last.isEmpty()) {
            args.add(last);
        }
        return args;
    }
}