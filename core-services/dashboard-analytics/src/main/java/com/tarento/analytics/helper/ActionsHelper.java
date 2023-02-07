package com.tarento.analytics.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.tarento.analytics.dto.Data;
import com.tarento.analytics.dto.Plot;
import com.tarento.analytics.handler.IResponseHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
@Component
public class ActionsHelper {
    public List<Data> divide(String action, List<Data> dataList, JsonNode chartNode) {
        if (dataList.size() != 2 && dataList.get(0).getPlots().size() != dataList.get(1).getPlots().size()) {
            throw new RuntimeException("Plot sizes doesn't match to perform the action");
        }
        List<Data> plotList = new LinkedList<>();
        double cumulativeValue = 0.0;
        // Setting the label as the first field in aggregation paths
        plotList.add(new Data(chartNode.get(IResponseHandler.AGGS_PATH).get(0).asText(), cumulativeValue, chartNode.get(IResponseHandler.VALUE_TYPE).asText()));
        Iterator<Plot> numeratorIterator = dataList.get(0).getPlots().iterator();
        Iterator<Plot> denominatorIterator = dataList.get(1).getPlots().iterator();
        while(numeratorIterator.hasNext() && denominatorIterator.hasNext()) {
            Plot numeratorPlot = numeratorIterator.next();
            Plot denominatorPlot = denominatorIterator.next();
            if (!numeratorPlot.getName().equals(denominatorPlot.getName())) {
                throw new RuntimeException("Plot names doesn't match");
            }
            double numerator= numeratorPlot.getValue();
            double denominator = denominatorPlot.getValue();
            double computedValue = 0.0;
            if (denominator == 0.0) {
                computedValue = 0.0;
            } else {
                computedValue = (numerator / denominator);
                if (action.equals(IResponseHandler.PERCENTAGE)) {
                    computedValue = computedValue * 100;
                }
            }
            cumulativeValue += computedValue;
            plotList.get(0).getPlots().add(new Plot(numeratorPlot.getName(), computedValue, chartNode.get(IResponseHandler.VALUE_TYPE).asText()));
        }
        plotList.get(0).setHeaderValue(cumulativeValue);
        return plotList;
    }
}
