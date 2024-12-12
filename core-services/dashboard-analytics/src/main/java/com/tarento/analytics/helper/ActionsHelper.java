package com.tarento.analytics.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.tarento.analytics.dto.Data;
import com.tarento.analytics.dto.Plot;
import com.tarento.analytics.handler.IResponseHandler;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ActionsHelper {
    public List<Data> divide(String action, String newHeaderName, List<Data> dataList, JsonNode chartNode) {
        if (dataList.size() != 2 || dataList.get(0).getPlots().size() > dataList.get(1).getPlots().size()) {
            throw new RuntimeException("Plot size of numerator is more than denominator to compute percentage");
        }
        List<Data> plotList = new LinkedList<>();
        String headerName = newHeaderName != null ? newHeaderName : dataList.get(0).getHeaderName();
        double cumulativeValue = 0.0;
        List<Plot> plots = new ArrayList<>();
        // Setting the label of plot as the 0th element in aggregation paths array
        plotList.add(new Data(headerName, cumulativeValue, chartNode.get(IResponseHandler.VALUE_TYPE).asText()));
        Map<String, Double> numeratorMap= new HashMap<String, Double>();
        Map<String, Double> denominatorMap= new HashMap<String, Double>();
        dataList.get(0).getPlots().forEach(plot -> {
            numeratorMap.put(plot.getName(), plot.getValue());
        });
        dataList.get(1).getPlots().forEach(plot -> {
            denominatorMap.put(plot.getName(), plot.getValue());
        });
        for (Map.Entry<String, Double> pair : denominatorMap.entrySet()) {
            double numerator = numeratorMap.getOrDefault(pair.getKey(), 0.0);
            double computedValue = 0.0;
            if (pair.getValue() == 0.0) {
                computedValue = 0.0;
            } else {
                computedValue = (numerator / pair.getValue());
                if (action.equals(IResponseHandler.PERCENTAGE)) {
                    computedValue = computedValue * 100;
                }
            }
            cumulativeValue += computedValue;
            plotList.get(0).getPlots().add(new Plot(pair.getKey(), computedValue, chartNode.get(IResponseHandler.VALUE_TYPE).asText()));
        }
        plotList.get(0).setHeaderValue(cumulativeValue);
        return plotList;
    }
    public List<Data> divisionByConstant(String action, List<Data> dataList, JsonNode chartNode, Double divisorValue) {
        if (dataList.size() != 2 ) {
            throw new RuntimeException("Data is not eligible for division by constant");
        }
        List<Plot> plotList = new ArrayList<>();
        List<Plot> updatedPlots = new ArrayList<>();
        Data data = dataList.get(0);
        data.getPlots().forEach((plot) -> {
            double v = plot.getValue() / divisorValue * 100;
            Plot dividedPlot = new Plot(plot.getName(), v, IResponseHandler.PERCENTAGE);
            updatedPlots.add(dividedPlot);
        });
        dataList.get(0).setPlots(updatedPlots);
        dataList.remove(1);

        return dataList;

    }
}
