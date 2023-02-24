package com.tarento.analytics.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.tarento.analytics.dto.AggregateRequestDto;
import com.tarento.analytics.dto.Data;
import com.tarento.analytics.dto.Plot;
import com.tarento.analytics.handler.IResponseHandler;
import org.springframework.stereotype.Component;

import java.util.*;


@Component
public class SumComputedField implements IComputedField<List<Data>>{

    private String postAggrTheoryName;
    private AggregateRequestDto aggregateRequestDto;

    @Override
    public void set(AggregateRequestDto requestDto, String postAggrTheoryName){
        this.aggregateRequestDto = requestDto;
        this.postAggrTheoryName = postAggrTheoryName;
    }
    @Override
    public void add(List<Data> dataList, List<String> fields, String newField, JsonNode chartNode) {
        List<Data> newDataList = new ArrayList<>();
        Map<String, Double> sumMap= new HashMap<String, Double>();
        dataList.get(0).getPlots().forEach(plot -> {
            sumMap.put(plot.getName(), 0.0);
        });
        Set<String> keys = new HashSet<>(sumMap.keySet());
        for(int i =0 ; i< dataList.size(); i++) {
            if(!fields.contains(dataList.get(i).getHeaderName())) {
                newDataList.add(dataList.get(i));
            } else {
                Map<String, Double> tempMap= new HashMap<String, Double>();
                dataList.get(i).getPlots().forEach(plot -> {
                    tempMap.put(plot.getName(), plot.getValue());
                });
                for (String k : keys) {
                    sumMap.put(k, sumMap.getOrDefault(k, 0.0) + tempMap.getOrDefault(k, 0.0) );
                }
            }
        }
        double cumulativeValue = sumMap.values().stream().reduce(0.0, Double::sum);
        Data summedUpData = new Data(newField, cumulativeValue, chartNode.get(IResponseHandler.VALUE_TYPE).asText());
        List<Plot> plots = new ArrayList<>();
        for(String k : keys) {
            plots.add(new Plot(k, sumMap.getOrDefault(k , 0.0), chartNode.get(IResponseHandler.VALUE_TYPE).asText()));
        }
        summedUpData.setPlots(plots);
        newDataList.add(summedUpData);
        dataList.clear();
        dataList.addAll(newDataList);
    }
}
