package com.tarento.analytics.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.analytics.dto.AggregateRequestDto;
import com.tarento.analytics.dto.Data;
import com.tarento.analytics.handler.IResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.tarento.analytics.constant.Constants.PostAggregationTheories.RESPONSE_DIFF_DATES;
import static com.tarento.analytics.handler.IResponseHandler.IS_CAPPED_BY_CAMPAIGN_PERIOD;

@Component
public class NoOpsComputedField implements IComputedField<ObjectNode>{

    public static final Logger logger = LoggerFactory.getLogger(NoOpsComputedField.class);


    private String postAggrTheoryName;
    private AggregateRequestDto aggregateRequestDto;
    @Autowired
    private ComputeHelperFactory computeHelperFactory;

    @Override
    public void set(AggregateRequestDto requestDto, String postAggrTheoryName){
        this.aggregateRequestDto = requestDto;
        this.postAggrTheoryName = postAggrTheoryName;
    }

    @Override
    public void add(ObjectNode data, List<String> fields, String newField, JsonNode chartNode ) {
        ObjectNode noOpsNode = JsonNodeFactory.instance.objectNode();
        List<Data> dataList = new ArrayList<>();
        List<Data> capDataList = new ArrayList<>();
        try {
            List<JsonNode> values = data.findValues(fields.get(0));
            if (postAggrTheoryName.equalsIgnoreCase(RESPONSE_DIFF_DATES)) {
                for(JsonNode valueNode : values){
                    Long val = valueNode.get(IResponseHandler.VALUE).asLong();
                    Data dataNode = new Data(fields.get(0), val.doubleValue(), chartNode.get(IResponseHandler.VALUE_TYPE).asText());
                    dataList.add(dataNode);
                }

                if (chartNode.has(IS_CAPPED_BY_CAMPAIGN_PERIOD) && data.has((chartNode.get(IS_CAPPED_BY_CAMPAIGN_PERIOD).get(0).asText()))) {
                    Long capValue = data.findValues(chartNode.get(IS_CAPPED_BY_CAMPAIGN_PERIOD).get(0).asText()).get(0).get(IResponseHandler.VALUE).asLong();
                    Data dataNode = new Data(fields.get(0), capValue.doubleValue(), chartNode.get(IResponseHandler.VALUE_TYPE).asText());
                    capDataList.add(dataNode);
                }
                ComputeHelper computeHelper = computeHelperFactory.getInstance(RESPONSE_DIFF_DATES);
                List<Data> computedData = computeHelper.compute(aggregateRequestDto, dataList, capDataList);
                noOpsNode.put(IResponseHandler.VALUE, ((Double) computedData.get(0).getHeaderValue()).longValue());
                data.set(newField, noOpsNode);
            }


        } catch (Exception e) {
            logger.error("Computed field configuration not correctly provided " +e.getMessage());
            noOpsNode.put(IResponseHandler.VALUE,0);
            data.set(newField, noOpsNode);
        }

    }
}
