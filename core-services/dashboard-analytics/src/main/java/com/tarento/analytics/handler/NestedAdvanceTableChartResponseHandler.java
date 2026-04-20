package com.tarento.analytics.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.analytics.dto.AggregateDto;
import com.tarento.analytics.dto.AggregateRequestDto;
import com.tarento.analytics.dto.Data;
import com.tarento.analytics.dto.Plot;
import com.tarento.analytics.enums.ChartType;
import com.tarento.analytics.helper.ComputedFieldFactory;
import com.tarento.analytics.helper.IComputedField;
import com.tarento.analytics.helper.SortingHelper;
import com.tarento.analytics.model.ComputedFields;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Flattens nested ES bucket aggregations into XTABLE rows.
 *
 * Config shape (in chart node):
 * - bucketHierarchy: [{ "aggName": "Country", "plotName": "Country" }, ...]
 * - metricPaths: [{ "name": "Present Count", "path": "Present.Present Count.value", "valueType": "number" }, ...]
 */
@Component
public class NestedAdvanceTableChartResponseHandler implements IResponseHandler {
    private static final Logger logger = LoggerFactory.getLogger(NestedAdvanceTableChartResponseHandler.class);

    private static final String BUCKET_HIERARCHY = "bucketHierarchy";
    private static final String METRIC_PATHS = "metricPaths";
    private static final String HEADER_JOINER = "headerJoiner";
    private static final String SPLIT_METRICS_PATHS = "splitMetricsPaths";
    private static final String SPLIT_METRICS_STR_VALUE = "splitMetricsBy";
    private static final String IGNORE_SPLIT_METRIC_PATHS = "ignoreSplitMetricPaths";

    private static final String H_AGG_NAME = "aggName";
    private static final String H_PLOT_NAME = "plotName";

    private static final String M_NAME = "name";
    private static final String M_PATH = "path";
    private static final String M_VALUE_TYPE = "valueType";

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private SortingHelper sortingHelper;

    @Autowired
    private ComputedFieldFactory computedFieldFactory;

    @Override
    public AggregateDto translate(AggregateRequestDto requestDto, ObjectNode aggregations) throws IOException {
        JsonNode aggregationNode = aggregations.get(AGGREGATIONS);
        JsonNode chartNode = requestDto.getChartNode();

        ArrayNode hierarchy = chartNode.has(BUCKET_HIERARCHY) && chartNode.get(BUCKET_HIERARCHY).isArray()
                ? (ArrayNode) chartNode.get(BUCKET_HIERARCHY)
                : null;
        if (hierarchy == null || hierarchy.isEmpty()) {
            return getAggregatedDto(chartNode, Collections.emptyList(), requestDto.getVisualizationCode());
        }

        String headerJoiner = chartNode.has(HEADER_JOINER) ? chartNode.get(HEADER_JOINER).asText(" | ") : " | ";

        List<LevelSpec> levels = parseLevels(hierarchy);
        if (levels.isEmpty()) {
            return getAggregatedDto(chartNode, Collections.emptyList(), requestDto.getVisualizationCode());
        }

        List<MetricSpec> metrics = parseMetrics(chartNode.get(METRIC_PATHS));
        List<String> splitAggNames = parseStringList(chartNode.get(SPLIT_METRICS_PATHS));
        Set<String> ignoreSplitMetricNames = new HashSet<>(parseStringList(chartNode.get(IGNORE_SPLIT_METRIC_PATHS)));
        SplitSpec splitSpec = buildSplitSpec(levels, splitAggNames);

        JsonNode topAgg = aggregationNode == null ? null : aggregationNode.findValue(levels.get(0).aggName);
        ArrayNode topBuckets = topAgg != null && topAgg.has(BUCKETS) && topAgg.get(BUCKETS).isArray()
                ? (ArrayNode) topAgg.get(BUCKETS)
                : null;
        if (topBuckets == null) {
            return getAggregatedDto(chartNode, Collections.emptyList(), requestDto.getVisualizationCode());
        }

        List<Data> rows = new ArrayList<>();
        int[] idx = {1};

        for (JsonNode bucket : topBuckets) {
            flatten(levels, 0, bucket, new LinkedHashMap<>(), metrics, chartNode, headerJoiner, idx, rows);
        }

        // computedFields (same behavior as XTABLE)
        if (chartNode.has(COMPUTED_FIELDS) && chartNode.get(COMPUTED_FIELDS).isArray()) {
            applyComputedFields(requestDto, chartNode, rows);
        }

        // excludedColumns
        if (chartNode.has(EXCLUDED_COLUMNS) && chartNode.get(EXCLUDED_COLUMNS).isArray()) {
            Set<String> excluded = mapper.convertValue(chartNode.get(EXCLUDED_COLUMNS), new TypeReference<List<String>>() {})
                    .stream().collect(Collectors.toSet());
            rows.forEach(d -> d.getPlots().removeIf(p -> excluded.contains(p.getName())));
        }

        // splitMetricsPaths (after computedFields + excludedColumns so computedFields can use base metric names)
        if (splitSpec.enabled) {
            rows = splitRowsAfterCompute(rows, splitSpec, headerJoiner, ignoreSplitMetricNames);
        }

        // XtableColumnOrder
        if (chartNode.get(IResponseHandler.CHART_SPECIFIC) != null) {
            JsonNode specificData = chartNode.get(IResponseHandler.CHART_SPECIFIC);
            JsonNode orderColumns = specificData.get(IResponseHandler.XTABLE_COLUMN);
            if (orderColumns != null && orderColumns.isArray()) {
                rows.forEach(r -> {
                    List<Plot> newPlots = new ArrayList<>();
                    orderColumns.forEach(col -> {
                        String colName = col.asText();
                        r.getPlots().stream()
                                .filter(p -> p.getName().equals(colName))
                                .findFirst()
                                .ifPresent(newPlots::add);
                    });
                    if (!newPlots.isEmpty()) {
                        r.setPlots(newPlots);
                    }
                });
            }
        }

        if (chartNode.has("sort")) {
            rows = sortingHelper.tableSort(rows, chartNode.get("sort").asText());
        }

        AggregateDto dto = getAggregatedDto(chartNode, rows, requestDto.getVisualizationCode());
        dto.setChartType(ChartType.XTABLE); // keep response compatible with existing xtable consumers
        return dto;
    }

    private void flatten(
            List<LevelSpec> levels,
            int levelIndex,
            JsonNode bucket,
            LinkedHashMap<String, String> pathLabelsByPlotName,
            List<MetricSpec> metrics,
            JsonNode chartNode,
            String headerJoiner,
            int[] idx,
            List<Data> out
    ) {
        LevelSpec level = levels.get(levelIndex);
        String key = bucketKey(bucket);
        pathLabelsByPlotName.put(level.plotName, key);

        boolean isLeaf = levelIndex == (levels.size() - 1);
        if (!isLeaf) {
            String nextAggName = levels.get(levelIndex + 1).aggName;
            JsonNode nextAgg = bucket.get(nextAggName);
            List<JsonNode> nextBuckets = extractBuckets(nextAgg);
            if (nextBuckets.isEmpty()) {
                return;
            }
            for (JsonNode childBucket : nextBuckets) {
                flatten(levels, levelIndex + 1, childBucket, new LinkedHashMap<>(pathLabelsByPlotName), metrics, chartNode, headerJoiner, idx, out);
            }
            return;
        }

        List<Plot> plots = new ArrayList<>();

        Plot sno = new Plot(SERIAL_NUMBER, TABLE_TEXT);
        sno.setLabel(String.valueOf(idx[0]));
        plots.add(sno);

        for (LevelSpec lvl : levels) {
            Plot p = new Plot(lvl.plotName, TABLE_TEXT);
            p.setLabel(pathLabelsByPlotName.get(lvl.plotName));
            plots.add(p);
        }

        for (MetricSpec ms : metrics) {
            Double v = resolveNumber(bucket, ms.path);
            if (chartNode.get(IS_ROUND_OFF) != null && chartNode.get(IS_ROUND_OFF).asBoolean()) {
                v = (double) Math.round(v);
            }
            plots.add(new Plot(ms.name, v, ms.valueType));
        }

        List<String> headerParts = levels.stream()
                .map(l -> pathLabelsByPlotName.get(l.plotName))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        String headerName = String.join(headerJoiner, headerParts);
        Data data = new Data(headerName, idx[0]++, null);
        data.setPlots(plots);
        out.add(data);
    }

    private List<Data> splitRowsAfterCompute(List<Data> rows, SplitSpec splitSpec, String headerJoiner, Set<String> ignoreSplitMetricNames) {
        LinkedHashMap<String, SplitRow> grouped = new LinkedHashMap<>();

        for (Data row : rows) {
            Map<String, Plot> plotsByName = row.getPlots().stream()
                    .collect(Collectors.toMap(Plot::getName, p -> p, (a, b) -> a, LinkedHashMap::new));

            List<String> prefixLabels = new ArrayList<>();
            for (int i = 0; i < splitSpec.prefixLevelsCount; i++) {
                Plot p = plotsByName.get(splitSpec.levels.get(i).plotName);
                if (p != null && StringUtils.isNotBlank(p.getLabel())) prefixLabels.add(p.getLabel());
            }
            String prefixKey = String.join(headerJoiner, prefixLabels);

            List<String> splitLabels = new ArrayList<>();
            for (int i = splitSpec.prefixLevelsCount; i < splitSpec.levels.size(); i++) {
                Plot p = plotsByName.get(splitSpec.levels.get(i).plotName);
                if (p != null && StringUtils.isNotBlank(p.getLabel())) splitLabels.add(p.getLabel());
            }
            String splitJoined = String.join(headerJoiner, splitLabels);

            SplitRow acc = grouped.get(prefixKey);
            if (acc == null) {
                acc = new SplitRow();
                acc.prefixLabelsByPlotName = new LinkedHashMap<>();
                for (int i = 0; i < splitSpec.prefixLevelsCount; i++) {
                    String plotName = splitSpec.levels.get(i).plotName;
                    Plot p = plotsByName.get(plotName);
                    acc.prefixLabelsByPlotName.put(plotName, p == null ? null : p.getLabel());
                }
                grouped.put(prefixKey, acc);
            }

            for (Plot p : row.getPlots()) {
                if (SERIAL_NUMBER.equals(p.getName())) continue;
                if (isHierarchyPlot(splitSpec, p.getName())) continue; // drop Country/State/... from split metrics section
                if (ignoreSplitMetricNames != null && ignoreSplitMetricNames.contains(p.getName())) continue;
                String newName = splitJoined + headerJoiner + p.getName();
                Plot np = new Plot(newName, p.getValue(), p.getSymbol());
                np.setStrValue(SPLIT_METRICS_STR_VALUE);
                acc.metricPlots.put(newName, np);
            }
        }

        List<Data> out = new ArrayList<>();
        int sno = 1;
        for (Map.Entry<String, SplitRow> e : grouped.entrySet()) {
            SplitRow sr = e.getValue();
            List<Plot> plots = new ArrayList<>();

            Plot snoPlot = new Plot(SERIAL_NUMBER, TABLE_TEXT);
            snoPlot.setLabel(String.valueOf(sno));
            plots.add(snoPlot);

            for (int i = 0; i < splitSpec.prefixLevelsCount; i++) {
                LevelSpec lvl = splitSpec.levels.get(i);
                Plot p = new Plot(lvl.plotName, TABLE_TEXT);
                p.setLabel(sr.prefixLabelsByPlotName.get(lvl.plotName));
                plots.add(p);
            }

            plots.addAll(sr.metricPlots.values());

            Data d = new Data(e.getKey(), sno++, null);
            d.setPlots(plots);
            out.add(d);
        }

        return out;
    }

    private boolean isHierarchyPlot(SplitSpec splitSpec, String plotName) {
        for (LevelSpec l : splitSpec.levels) {
            if (l.plotName.equals(plotName)) return true;
        }
        return false;
    }

    private void applyComputedFields(AggregateRequestDto requestDto, JsonNode chartNode, List<Data> rows) {
        try {
            List<ComputedFields> computedFieldsList = mapper.readValue(
                    chartNode.get(COMPUTED_FIELDS).toString(),
                    new TypeReference<List<ComputedFields>>() {}
            );
            for (Data row : rows) {
                for (ComputedFields cfs : computedFieldsList) {
                    IComputedField computedFieldObject = computedFieldFactory.getInstance(cfs.getActionName());
                    computedFieldObject.set(requestDto, cfs.getPostAggregationTheory());
                    computedFieldObject.add(row, cfs.getFields(), cfs.getNewField(), chartNode);
                }
            }
        } catch (Exception e) {
            logger.error("computedFields failed: {}", e.getMessage());
        }
    }

    private String bucketKey(JsonNode bucket) {
        JsonNode keyAsString = bucket.get(KEY_AS_STRING);
        if (keyAsString != null && !keyAsString.asText().isEmpty()) {
            return keyAsString.asText();
        }
        JsonNode key = bucket.get(KEY);
        return key == null ? "" : key.asText();
    }

    /**
     * Supports both:
     * - terms/date_histogram: { "buckets": [ { "key": ... }, ... ] }
     * - filters: { "buckets": { "A": { "doc_count":.. }, "OTHER": {..} } }
     *
     * For filters, we inject "key" from the map key.
     */
    private List<JsonNode> extractBuckets(JsonNode aggNode) {
        if (aggNode == null || !aggNode.has(BUCKETS)) return Collections.emptyList();
        JsonNode buckets = aggNode.get(BUCKETS);
        if (buckets == null) return Collections.emptyList();

        if (buckets.isArray()) {
            List<JsonNode> out = new ArrayList<>();
            buckets.forEach(out::add);
            return out;
        }

        if (buckets.isObject()) {
            List<JsonNode> out = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> it = buckets.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (e.getValue() != null && e.getValue().isObject()) {
                    ObjectNode copy = e.getValue().deepCopy();
                    if (!copy.has(KEY)) {
                        copy.put(KEY, e.getKey());
                    }
                    out.add(copy);
                }
            }
            return out;
        }

        return Collections.emptyList();
    }

    private Double resolveNumber(JsonNode base, String dotPath) {
        JsonNode n = resolvePath(base, dotPath);
        if (n == null || n.isMissingNode() || n.isNull()) {
            return 0.0;
        }
        if (n.isNumber()) {
            return n.asDouble();
        }
        JsonNode valueNode = n.get(VALUE);
        if (valueNode != null && valueNode.isNumber()) {
            return valueNode.asDouble();
        }
        JsonNode docCountNode = n.get(DOC_COUNT);
        if (docCountNode != null && docCountNode.isNumber()) {
            return docCountNode.asDouble();
        }
        return 0.0;
    }

    private JsonNode resolvePath(JsonNode base, String dotPath) {
        if (base == null || dotPath == null || dotPath.isEmpty()) {
            return null;
        }
        JsonNode cur = base;
        for (String part : dotPath.split("\\.")) {
            if (cur == null) {
                return null;
            }
            cur = cur.get(part);
        }
        return cur;
    }

    private List<LevelSpec> parseLevels(ArrayNode hierarchy) {
        List<LevelSpec> out = new ArrayList<>();
        for (JsonNode n : hierarchy) {
            if (!n.isObject()) continue;
            String aggName = n.has(H_AGG_NAME) ? n.get(H_AGG_NAME).asText() : null;
            String plotName = n.has(H_PLOT_NAME) ? n.get(H_PLOT_NAME).asText() : null;
            if (StringUtils.isBlank(aggName) || StringUtils.isBlank(plotName)) continue;
            out.add(new LevelSpec(aggName, plotName));
        }
        return out;
    }

    private List<MetricSpec> parseMetrics(JsonNode metricPathsNode) {
        if (metricPathsNode == null || !metricPathsNode.isArray()) {
            return Collections.emptyList();
        }
        List<MetricSpec> out = new ArrayList<>();
        for (JsonNode n : metricPathsNode) {
            if (!n.isObject()) continue;
            String name = n.has(M_NAME) ? n.get(M_NAME).asText() : null;
            String path = n.has(M_PATH) ? n.get(M_PATH).asText() : null;
            String valueType = n.has(M_VALUE_TYPE) ? n.get(M_VALUE_TYPE).asText() : "number";
            if (StringUtils.isBlank(name) || StringUtils.isBlank(path)) continue;
            out.add(new MetricSpec(name, path, valueType));
        }
        return out;
    }

    private static class LevelSpec {
        final String aggName;
        final String plotName;

        LevelSpec(String aggName, String plotName) {
            this.aggName = aggName;
            this.plotName = plotName;
        }
    }

    private static class MetricSpec {
        final String name;
        final String path;
        final String valueType;

        MetricSpec(String name, String path, String valueType) {
            this.name = name;
            this.path = path;
            this.valueType = valueType;
        }
    }

    private List<String> parseStringList(JsonNode node) {
        if (node == null || !node.isArray()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        node.forEach(n -> {
            if (n != null && n.isTextual() && StringUtils.isNotBlank(n.asText())) out.add(n.asText());
        });
        return out;
    }

    private SplitSpec buildSplitSpec(List<LevelSpec> levels, List<String> splitAggNames) {
        SplitSpec spec = new SplitSpec();
        spec.levels = levels;
        spec.enabled = false;
        spec.prefixLevelsCount = levels.size();

        if (splitAggNames == null || splitAggNames.isEmpty()) return spec;
        if (splitAggNames.size() >= levels.size()) return spec;

        // must be suffix of aggName list, in order
        int start = levels.size() - splitAggNames.size();
        for (int i = 0; i < splitAggNames.size(); i++) {
            if (!levels.get(start + i).aggName.equals(splitAggNames.get(i))) {
                return spec; // invalid -> ignore split
            }
        }
        spec.enabled = true;
        spec.prefixLevelsCount = start;
        return spec;
    }

    private String joinLabels(List<LevelSpec> levels, int fromInclusive, int toExclusive,
                              LinkedHashMap<String, String> labelsByPlotName, String joiner) {
        List<String> parts = new ArrayList<>();
        for (int i = fromInclusive; i < toExclusive; i++) {
            String v = labelsByPlotName.get(levels.get(i).plotName);
            if (v != null) parts.add(v);
        }
        return String.join(joiner, parts);
    }

    private static class SplitSpec {
        boolean enabled;
        int prefixLevelsCount;
        List<LevelSpec> levels;
    }

    private static class SplitRow {
        LinkedHashMap<String, String> prefixLabelsByPlotName;
        LinkedHashMap<String, Plot> metricPlots = new LinkedHashMap<>();
    }
}

