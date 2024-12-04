package com.tarento.analytics.handler;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarento.analytics.helper.ActionsHelper;
import com.tarento.analytics.helper.ComputedFieldFactory;
import com.tarento.analytics.helper.IComputedField;
import com.tarento.analytics.helper.SortingHelper;
import com.tarento.analytics.model.ComputedFields;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.analytics.constant.Constants;
import com.tarento.analytics.dto.AggregateDto;
import com.tarento.analytics.dto.AggregateRequestDto;
import com.tarento.analytics.dto.Data;
import com.tarento.analytics.dto.Plot;

import static com.tarento.analytics.constant.Constants.JsonPaths.DAYS;

/**
 * This handles ES response for single index, multiple index to represent data as line chart
 * Creates plots by merging/computing(by summation) index values for same key
 * AGGS_PATH : this defines the path/key to be used to search the tree
 * VALUE_TYPE : defines the data type for the value formed, this could be amount, percentage, number
 *
 */
@Component
public class LineChartResponseHandler implements IResponseHandler {
    public static final Logger logger = LoggerFactory.getLogger(LineChartResponseHandler.class);
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private ComputedFieldFactory computedFieldFactory;
    @Autowired
    private ActionsHelper actionsHelper;
    @Autowired
    private SortingHelper sortingHelper;

    @Override
    public AggregateDto translate(AggregateRequestDto requestDto, ObjectNode aggregations) throws IOException {

        List<Data> dataList = new LinkedList<>();

        //String json = "{\"ptindex-v1\":{\"Closed Application\":{\"buckets\":[{\"key_as_string\":\"2018-11-12T00:00:00.000Z\",\"key\":1541980800000,\"doc_count\":1,\"Applications Closed\":{\"buckets\":{\"closed\":{\"doc_count\":0,\"Count\":{\"value\":0}}}}}]},\"Total Application\":{\"buckets\":[{\"key_as_string\":\"2018-11-12T00:00:00.000Z\",\"key\":1541980800000,\"doc_count\":1,\"Count\":{\"value\":1}}]}},\"tlindex-v1\":{\"Closed Application\":{\"buckets\":[{\"key_as_string\":\"2019-04-29T00:00:00.000Z\",\"key\":1556496000000,\"doc_count\":6,\"Applications Closed\":{\"buckets\":{\"closed\":{\"doc_count\":0,\"Count\":{\"value\":0}},\"resolved\":{\"doc_count\":0,\"Count\":{\"value\":0}}}}}]},\"Total Application\":{\"buckets\":[{\"key\":1555891200000,\"doc_count\":1,\"Count\":{\"value\":1}},{\"key\":1556496000000,\"doc_count\":0,\"Count\":{\"value\":0}}]}},\"pgrindex-v1\":{\"Closed Application\":{\"buckets\":[{\"key\":1564963200000,\"doc_count\":438,\"Applications Closed\":{\"buckets\":{\"closed\":{\"doc_count\":5,\"Count\":{\"value\":5}}}}}]},\"Total Application\":{\"buckets\":[{\"key\":1564963200000,\"doc_count\":438,\"Count\":{\"value\":438}},{\"key\":1574035200000,\"doc_count\":3,\"Count\":{\"value\":3}}]}}}";
        JsonNode aggregationNode = aggregations.get(AGGREGATIONS);
        JsonNode chartNode = requestDto.getChartNode();
        boolean isRequestInterval = null == requestDto.getRequestDate() ? false : requestDto.getRequestDate().getInterval()!=null && !requestDto.getRequestDate().getInterval().isEmpty();
        String interval = isRequestInterval ? requestDto.getRequestDate().getInterval(): chartNode.get(Constants.JsonPaths.INTERVAL).asText();
        if(interval == null || interval.isEmpty()){
            throw new RuntimeException("Interval must have value from config or request");
        }

        String symbol = chartNode.get(IResponseHandler.VALUE_TYPE).asText();
        String symbolFromPathDataTypeMap = symbol;
        ArrayNode aggrsPaths = (ArrayNode) chartNode.get(IResponseHandler.AGGS_PATH);
        ArrayNode pathDataTypeMap = (ArrayNode) chartNode.get(TYPE_MAPPING);

        Set<String> plotKeys = new LinkedHashSet<>();
        List<Long> targetEpochKeys = new ArrayList<>();
        List<Long> actualEpochKeys = new ArrayList<>();
        boolean isCumulative = chartNode.get("isCumulative").asBoolean();

        JsonNode computedFields = chartNode.get(COMPUTED_FIELDS);
        JsonNode predictionPath = chartNode.get(PREDICTION_PATH);
        JsonNode distributionPath = chartNode.get(DISTRIBUTION_PATH);
        JsonNode cappedTarget = chartNode.get(ACTUAL_VS_PLANNED_CAPPED_TARGET);
        Long startDate = null;
        Long endDate = null;
        Long cappedTargetValue;
        boolean executeComputedFields = computedFields !=null && computedFields.isArray();
        boolean isPredictionEnabled = predictionPath!=null;
        String action = chartNode.get(ACTION).asText();
        Double divisorValues = 1.0;
        boolean showPercentageValue = chartNode.has(SHOW_PERCENTAGE_VALUE) ? chartNode.get(SHOW_PERCENTAGE_VALUE).asBoolean() : false;
        boolean appendDistributionPlot = chartNode.has(APPEND_DISTRIBUTION_PLOT) ? chartNode.get(APPEND_DISTRIBUTION_PLOT).asBoolean() : false;
        if(isPredictionEnabled ) {
            List<JsonNode> aggrNodes = aggregationNode.findValues(CHART_SPECIFIC);
            startDate = (aggrNodes.get(0).findValues(START_DATE).get(0).findValues("key").get(0).asLong()/86400000)*86400000;
            endDate = (aggrNodes.get(0).findValues(END_DATE).get(0).findValues("key").get(0).asLong()/86400000)*86400000;
            cappedTargetValue = (aggrNodes.get(0).findValues(cappedTarget.asText()).get(0).findValues("value").get(0).asLong());
            interval=Constants.Interval.day.toString();
            addTargetDates(startDate, endDate,targetEpochKeys);
        } else if(appendDistributionPlot) {
            List<JsonNode> aggrNodes = aggregationNode.findValues(CHART_SPECIFIC);
            startDate = (aggrNodes.get(0).findValues(START_DATE).get(0).findValues("key").get(0).asLong()/86400000)*86400000;
            cappedTargetValue = (aggrNodes.get(0).findValues(cappedTarget.asText()).get(0).findValues("value").get(0).asLong());
        } else {
            cappedTargetValue = null;
        }
        //aggrsPaths.forEach(headerPath -> {
        for(JsonNode headerPath : aggrsPaths){

            List<JsonNode> aggrNodes = aggregationNode.findValues(headerPath.asText());

            JsonNode datatype = null;
            if(pathDataTypeMap!=null) {
            	datatype = pathDataTypeMap.findValue(headerPath.asText());

            }

            if(datatype!=null) {
            	symbolFromPathDataTypeMap=datatype.asText();
            }
            else {
            	symbolFromPathDataTypeMap=symbol;
            }

            Map<String, Double> plotMap = new LinkedHashMap<>();
            Map<String, Double> multiAggrPlotMap = new LinkedHashMap<>();
            List<Double> totalValues = new ArrayList<>();
            Set<String> finalBucketKeys = new LinkedHashSet<>();

            // For multi aggr, find all plot keys first
//            enrichBucketKeys(aggrNodes, finalBucketKeys, interval);
            enrichBucketKeys(aggrNodes, finalBucketKeys, interval, startDate, isPredictionEnabled);
            initializeMultiAggrPlotMap(multiAggrPlotMap, finalBucketKeys);

            for(JsonNode aggrNode : aggrNodes) {
                if (aggrNode.findValues(IResponseHandler.BUCKETS).size() > 0) {
                    ArrayNode buckets = (ArrayNode) aggrNode.findValues(IResponseHandler.BUCKETS).get(0);
                    for(JsonNode bucket : buckets){
                            JsonNode bkey = bucket.findValue(IResponseHandler.KEY);
                            if (isPredictionEnabled && Long.parseLong(bkey.asText()) < startDate) {
                                continue;
                            }
                            String key = getIntervalKey(bkey.asText(), Constants.Interval.valueOf(interval));
                            plotKeys.add(key);
                            if((isPredictionEnabled || appendDistributionPlot) && headerPath.equals(distributionPath)){
                                    actualEpochKeys.add(bkey.asLong());
                            }
                            double previousVal = !isCumulative ? 0.0 : (totalValues.size() > 0 ? totalValues.get(totalValues.size() - 1) : 0.0);

                            double value = 0.0;
                            if (executeComputedFields) {
                                try {

                                    List<ComputedFields> computedFieldsList = mapper.readValue(computedFields.toString(), new TypeReference<List<ComputedFields>>() {
                                    });

                                for (ComputedFields cfs : computedFieldsList) {
                                    if (bucket.findValues(cfs.getFields().get(0)).isEmpty()) {
                                        value = getValueOfPlotMap(bucket, previousVal, chartNode);
                                        continue;
                                    }
                                    IComputedField computedFieldObject = computedFieldFactory.getInstance(cfs.getActionName());
                                    computedFieldObject.set(requestDto, cfs.getPostAggregationTheory());
                                    computedFieldObject.add(bucket, cfs.getFields(), cfs.getNewField(), chartNode);

                                        if (symbolFromPathDataTypeMap.equals(DAYS)) {

                                            long milidiff = bucket.findValue(cfs.getNewField()).get(IResponseHandler.VALUE).asLong();
                                            long days = TimeUnit.MILLISECONDS.toDays(milidiff);
                                            value = previousVal + (days);

                                        } else {
                                            value = previousVal + (bucket.findValue(cfs.getNewField()).get(IResponseHandler.VALUE).asLong());

                                        }
                                    }

                                } catch (Exception e) {
                                    logger.error("execution of computed field :" + e.getMessage());
                                }

                        } else {
                            value = getValueOfPlotMap(bucket, previousVal, chartNode);
                        }
                        //double value = previousVal + ((bucket.findValue(IResponseHandler.VALUE) != null) ? bucket.findValue(IResponseHandler.VALUE).asDouble():bucket.findValue(IResponseHandler.DOC_COUNT).asDouble());

                            plotMap.put(key, new Double("0") + value);
                            totalValues.add(value);
                        }
                }else if(action.equals(IResponseHandler.DIVISIONBYCONSTANT)){
                    divisorValues = (aggrNode.findValues(IResponseHandler.VALUE) != null && aggrNode.findValues(IResponseHandler.VALUE).size() == 1) ? aggrNode.findValues(IResponseHandler.VALUE).get(0).asDouble() : 1.0;

                }
                addIterationResultsToMultiAggrMap(plotMap, multiAggrPlotMap, isCumulative);
                plotMap.clear();
                totalValues.clear();
            }

            plotMap = multiAggrPlotMap;

            totalValues = new ArrayList<>(plotMap.values());
            String finalSymbolForPlots= symbolFromPathDataTypeMap;
            List<Plot> plots = plotMap.entrySet().stream().map(e -> new Plot(e.getKey(), e.getValue(), finalSymbolForPlots)).collect(Collectors.toList());
            try{
                Data data = new Data(headerPath.asText(), (totalValues==null || totalValues.isEmpty()) ? 0.0 : totalValues.stream().reduce(0.0, Double::sum),finalSymbolForPlots );
                data.setPlots(plots);
                dataList.add(data);
            } catch (Exception e) {
                logger.error(" Legend/Header "+headerPath.asText() +" exception occurred "+e.getMessage());
            }
            // });
        }


        Long finalStartDate = startDate;
        dataList.forEach(data -> {
            if(isPredictionEnabled) {
                if(data.getHeaderName().equals(predictionPath.asText())) {
                    appendTargetPlot(targetEpochKeys, data, symbol, isCumulative, cappedTargetValue);
                } else {
                    appendActualPlot(actualEpochKeys, finalStartDate,data,symbol,isCumulative);
                }
            } else if(appendDistributionPlot) {
                if(data.getHeaderName().equals(distributionPath.asText())) {
                    appendActualPlot(actualEpochKeys, finalStartDate,data,symbol,isCumulative);
                }
            } else {
                appendMissingPlot(plotKeys, data, symbol, isCumulative);
            }
        });
        if(isPredictionEnabled){
            addPredictionPlot(dataList,predictionPath,distributionPath,actualEpochKeys, finalStartDate);
        }

        if(chartNode.has(COMPUTE_MULTIPLE_PATHS) && chartNode.get(COMPUTE_MULTIPLE_PATHS).asBoolean()) {
            List<ComputedFields> computedFieldsList = mapper.readValue(computedFields.toString(), new TypeReference<List<ComputedFields>>() {
            });
            for (ComputedFields cfs : computedFieldsList) {

                IComputedField computedFieldObject = computedFieldFactory.getInstance(cfs.getActionName());
                computedFieldObject.set(requestDto, cfs.getPostAggregationTheory());
                computedFieldObject.add(dataList, cfs.getFields(), cfs.getNewField(), chartNode);

            }
        }

        if (action.equals(IResponseHandler.DIVISIONBYCONSTANT))  {
            dataList = actionsHelper.divisionByConstant(action, dataList, chartNode,divisorValues);
        }

        if (action.equals(PERCENTAGE) || action.equals(DIVISION))  {
            dataList = actionsHelper.divide(action, dataList, chartNode);
        }

        //"sort" key as final String
        if (computedFields != null && computedFields.size()!= 0 && computedFields.get(0).has(SORT)) {
            String sortingKey = computedFields.get(0).get(SORT).asText();
            dataList = sortingHelper.sort(sortingKey, dataList);
        }
        //We already have value sorting feature enabled, implementing the same for key sorting
        if (chartNode.has(SORT)) {
            String sortingKey = chartNode.get(SORT).asText();
            dataList = sortingHelper.sort(sortingKey, dataList);
        }

        if (showPercentageValue && cappedTargetValue!=null){
            dataList.forEach(data -> {
                data.setHeaderValue(((Double) data.getHeaderValue())*100/cappedTargetValue.doubleValue());
                data.getPlots().forEach(plot -> {
                    plot.setValue((plot.getValue()*100)/cappedTargetValue.doubleValue());
                });
            });
        }
        return getAggregatedDto(chartNode, dataList, requestDto.getVisualizationCode());
    }

    private void appendActualPlot(List<Long> actualEpochKeys, Long finalStartDate, Data data, String symbol, boolean isCumulative) {
        Long actualStartDate = null;
        if (actualEpochKeys.size() == 0) {
            actualStartDate = finalStartDate;
        } else {
            actualStartDate = actualEpochKeys.get(0);
        }
        Double differenceInDays = Math.ceil((actualStartDate - finalStartDate) / Constants.DAY_EPOCH);
        for (int i = 0; i < differenceInDays + 1; i++) {
            String name = getIntervalKey(String.valueOf(finalStartDate + Constants.DAY_EPOCH*i), Constants.Interval.day);
            data.getPlots().add(i,new Plot(name,0.0,symbol));
        }
    }

    public void addPredictionPlot(List<Data> dataList, JsonNode predictionPath, JsonNode distributionPath, List<Long> actualEpochKeys, Long finalStartDate) {
        Data targetPlot = dataList.stream().filter(ob -> ob.getHeaderName().equals(predictionPath.asText())).findFirst().get();
        Double overallTarget = targetPlot.getPlots().stream().reduce((first, second) -> second).get().getValue();
        Double targetPerDay = targetPlot.getPlots().get(0).getValue();

        Data distributionPlot = dataList.stream().filter(ob -> ob.getHeaderName().equals(distributionPath.asText())).findFirst().get();
        Double cumulativeValue = distributionPlot.getPlots().stream().reduce((first, second) -> second).get().getValue();
        Long dateToPredictFrom = null;
        if (actualEpochKeys.size() == 0) {
            dateToPredictFrom = finalStartDate;
        } else {
            dateToPredictFrom = actualEpochKeys.get(actualEpochKeys.size()-1);
        }
        Double differenceInData = overallTarget - cumulativeValue;
        Double daysToComplete = Math.ceil(differenceInData/targetPerDay);
        if(daysToComplete <= 0){
            return;
        }
        List<Plot> plots = new ArrayList<>();
        String plotSymbol = distributionPlot.getPlots().get(0).getSymbol();
        plots.add(new Plot(getIntervalKey(String.valueOf(dateToPredictFrom), Constants.Interval.day),cumulativeValue,plotSymbol));
        for (int i = 0; i < daysToComplete; i++) {
            dateToPredictFrom = dateToPredictFrom + Constants.DAY_EPOCH;
            cumulativeValue = cumulativeValue + ((i == daysToComplete - 1) ? differenceInData % targetPerDay : targetPerDay);
            plots.add(new Plot(getIntervalKey(String.valueOf(dateToPredictFrom), Constants.Interval.day),cumulativeValue,plotSymbol));
        }
        dataList.add(new Data("PREDICTION_".concat(distributionPlot.getHeaderName()), overallTarget,distributionPlot.getHeaderSymbol(),plots));
    }

    public void addTargetDates(Long startDate, Long endDate, List<Long> targetEpochKeys){
        targetEpochKeys.add(startDate);
        ListIterator<Long> iterator = targetEpochKeys.listIterator();
        while (targetEpochKeys.get(targetEpochKeys.size()-1) < endDate && targetEpochKeys.get(targetEpochKeys.size()-1) + Constants.DAY_EPOCH < endDate){
            targetEpochKeys.add(targetEpochKeys.get(targetEpochKeys.size()-1) + Constants.DAY_EPOCH);
        }
    }
    private void appendTargetPlot(List<Long> targetEpochKeys, Data data, String symbol, boolean isCumulative, Long cappedTargetValue){
        List<Plot> plots = new ArrayList<>();
        double targetValue = data.getPlots().get(0).getValue();
        if(isCumulative){
            int size = targetEpochKeys.size();
            for (int i = 0; i < size; i++) {
                Long key = targetEpochKeys.get(i);
                double value;
                if (i == size - 1) {
                    value = cappedTargetValue;
                } else {
                    value = plots.size() == 0 ? targetValue : plots.get(plots.size() - 1).getValue() + targetValue;
                }
                Plot plot = new Plot(getIntervalKey(String.valueOf(key), Constants.Interval.day), value, symbol);
                plots.add(plot);
            }

            data.setPlots(plots);
        }
    }
    private double getValueOfPlotMap(JsonNode bucket, double previousVal, JsonNode chartNode) {
        String jsonStr = bucket.toString();
        JSONObject currObj = new JSONObject(jsonStr);
        double value = 0.0;
        for (Iterator<String> it = bucket.fieldNames(); it.hasNext(); ) {
            String fieldName = it.next();
            if (currObj.get(fieldName) instanceof JSONObject) {
                if (bucket.get(fieldName).findValue("buckets") == null) {
                    value = previousVal + ((bucket.get(fieldName).findValue(IResponseHandler.VALUE) != null) ? bucket.get(fieldName).findValue(IResponseHandler.VALUE).asDouble() : bucket.get(fieldName).findValue(IResponseHandler.DOC_COUNT).asDouble());
                }
            }
        }

        //value = previousVal + ((bucket.findValue(IResponseHandler.VALUE) != null) ? bucket.findValue(IResponseHandler.VALUE).asDouble():bucket.findValue(IResponseHandler.DOC_COUNT).asDouble());

        if (chartNode.get(IS_ROUND_OFF) != null && chartNode.get(IS_ROUND_OFF).asBoolean()) {
            value = (double) Math.round(value);
        }
        return value;
    }

    private void addIterationResultsToMultiAggrMap(Map<String, Double> plotMap, Map<String, Double> multiAggrPlotMap, Boolean isCumulative) {

    	Map<String, Double> finalPlotMap = new LinkedHashMap<>();

        multiAggrPlotMap.keySet().forEach(key -> {
            finalPlotMap.put(key, 0.0);
        });

        plotMap.keySet().forEach(key -> {
            finalPlotMap.put(key, plotMap.get(key));
        });

    	if(isCumulative) {
             Double previousValue = 0.0;
             for (String key : finalPlotMap.keySet()) {
                 if (finalPlotMap.get(key) == 0.0)
                     finalPlotMap.put(key, previousValue);
                 previousValue = finalPlotMap.get(key);
             }
         }

    	finalPlotMap.keySet().forEach(key->{
            Double previousValue = multiAggrPlotMap.get(key);
            Double currentValue = finalPlotMap.get(key);
            multiAggrPlotMap.put(key, previousValue + currentValue);
        });

        if(isCumulative) {
            Double previousValue = 0.0;
            for (String key : multiAggrPlotMap.keySet()) {
                if (multiAggrPlotMap.get(key) == 0.0)
                    multiAggrPlotMap.put(key, previousValue);
                previousValue = multiAggrPlotMap.get(key);
            }
        }
    }

    private void initializeMultiAggrPlotMap(Map<String, Double> multiAggrPlotMap, Set<String> finalBucketKeys) {
        finalBucketKeys.forEach(keyName -> {
            multiAggrPlotMap.put(keyName, 0.0);
        });
    }

    private void enrichBucketKeys(List<JsonNode> aggrNodes, Set<String> finalBucketKeys, String interval, Long startDate, Boolean isPredictionEnabled) {
        List<String> bkeyList = new ArrayList<>();
        for(JsonNode aggrNode : aggrNodes) {
            if (aggrNode.findValues(IResponseHandler.BUCKETS).size() > 0) {
                ArrayNode buckets = (ArrayNode) aggrNode.findValues(IResponseHandler.BUCKETS).get(0);
                for(JsonNode bucket : buckets){
                    String bkey = bucket.findValue(IResponseHandler.KEY).asText();
                    if (isPredictionEnabled && Long.parseLong(bkey) < (startDate)) {
                        continue;
                    }
                    bkeyList.add(bkey);
                }
            }
        }
        Collections.sort(bkeyList);
        for(String bkey : bkeyList){
            String key = getIntervalKey(bkey, Constants.Interval.valueOf(interval));
            if(!finalBucketKeys.contains(key))
                finalBucketKeys.add(key);
        }

    }


    private String getIntervalKey(String epocString, Constants.Interval interval) {
        try {
            long epoch = Long.parseLong( epocString );
            Date expiry = new Date( epoch );
            Calendar cal = Calendar.getInstance();
            cal.setTime(expiry);

            String day = String.valueOf(cal.get(Calendar.DATE));
            String month = monthNames(cal.get(Calendar.MONTH)+1);
            String year =  ""+cal.get(Calendar.YEAR);

            String intervalKey = "";
            if(interval.equals(Constants.Interval.day)) {
                intervalKey = day.concat("-").concat(month);
            } else if(interval.equals(Constants.Interval.week)){
                intervalKey = day.concat("-").concat(month);
            } else if(interval.equals(Constants.Interval.year)){
                intervalKey = year;
            } else if(interval.equals(Constants.Interval.month)){
                intervalKey = month.concat("-").concat(year);
            } else {
                throw new RuntimeException("Invalid interval");
            }

            //String weekMonth = "Week " + cal.get(Calendar.WEEK_OF_YEAR)  /*+ " : " +  dayMonth*/;//+" of Month "+ (cal.get(Calendar.MONTH) + 1);
            return intervalKey;
        } catch (Exception e) {
            return epocString;
        }
    }

    private String monthNames(int month) {
        if(month == 1)
            return "Jan";
        else if(month == 2)
            return "Feb";
        else if(month == 3)
            return "Mar";
        else if(month == 4)
            return "Apr";
        else if(month == 5)
            return "May";
        else if(month == 6)
            return "Jun";
        else if(month == 7)
            return "Jul";
        else if(month == 8)
            return "Aug";
        else if(month == 9)
            return "Sep";
        else if(month == 10)
            return "Oct";
        else if(month == 11)
            return "Nov";
        else if(month == 12)
            return "Dec";
        else
            return "Month";
    }
}
