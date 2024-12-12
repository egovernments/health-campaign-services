package com.tarento.analytics.helper;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.tarento.analytics.dto.AggregateRequestDto;
import com.tarento.analytics.dto.Data;
import com.tarento.analytics.handler.IResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class StackPercentageComputedField implements IComputedField<List<Data>>{

    public static final Logger logger = LoggerFactory.getLogger(StackPercentageComputedField.class);

    private String postAggrTheoryName;
    private AggregateRequestDto aggregateRequestDto;

    @Autowired
    private ActionsHelper actionsHelper;

    @Override
    public void set(AggregateRequestDto requestDto, String postAggrTheoryName){
        this.aggregateRequestDto = requestDto;
        this.postAggrTheoryName = postAggrTheoryName;
    }
    @Override
    public void add(List<Data> dataList, List<String> fields, String newField, JsonNode chartNode) {
        if(fields == null || fields.size() != 2 || dataList.stream().noneMatch(data -> fields.contains(data.getHeaderName()))) {
            throw new RuntimeException("No match of fields in the aggregation paths");
        }
        List<Data> newDataList;
        Optional<Data> num = dataList.stream().filter(data -> fields.get(0).equals(data.getHeaderName()))
                .findAny();
        Optional<Data> denom = dataList.stream().filter(data -> fields.get(1).equals(data.getHeaderName()))
                .findAny();
        if(!num.isPresent() || !denom.isPresent()) return;
        newDataList = dataList.stream().filter(data -> !fields.contains(data.getHeaderName())).collect(Collectors.toList());
        List<Data> calculatedDataList = actionsHelper.divide(IResponseHandler.PERCENTAGE, newField, Arrays.asList(num.get(), denom.get()), chartNode);
        newDataList.addAll(calculatedDataList);
        dataList.clear();
        dataList.addAll(newDataList);
    }
}
