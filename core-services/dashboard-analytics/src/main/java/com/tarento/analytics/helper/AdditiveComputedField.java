package com.tarento.analytics.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.tarento.analytics.dto.AggregateRequestDto;
import com.tarento.analytics.dto.Data;
import com.tarento.analytics.dto.Plot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.tarento.analytics.handler.IResponseHandler.HIDE_HEADER_DENOMINATION;
import static com.tarento.analytics.handler.IResponseHandler.IS_CAPPED_BY_CAMPAIGN_PERIOD;

@Component
public class AdditiveComputedField implements IComputedField<Data> {

    public static final Logger logger = LoggerFactory.getLogger(AdditiveComputedField.class);


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
    public void add(Data data, List<String> fields, String newField,JsonNode chartNode ) {
        String dataType = chartNode.get(HIDE_HEADER_DENOMINATION).asBoolean() ? "number" : "amount";
        try {
            Map<String, Plot> plotMap = data.getPlots().stream().collect(Collectors.toMap(Plot::getName, Function.identity()));

            double total = 0.0;
            double capTotal = 0.0;
            for (String field: fields){
                if(plotMap.containsKey(field)){
                    dataType = plotMap.get(field).getSymbol();
                    if(chartNode.has(IS_CAPPED_BY_CAMPAIGN_PERIOD) && doesTextExistInArrayNode((ArrayNode) chartNode.get(IS_CAPPED_BY_CAMPAIGN_PERIOD), field)) continue;
                    total = total+ plotMap.get(field).getValue();
                }
            }
            if(postAggrTheoryName != null && !postAggrTheoryName.isEmpty()) {
                ComputeHelper computeHelper = computeHelperFactory.getInstance(postAggrTheoryName);
                if (chartNode.has(IS_CAPPED_BY_CAMPAIGN_PERIOD)) {
                    List<String> commonStrings = new ArrayList<>();
                    chartNode.get(IS_CAPPED_BY_CAMPAIGN_PERIOD).forEach(
                            item -> {
                                if (fields.contains(item.asText())) {
                                    commonStrings.add(item.asText());
                                }
                            }
                    );
                    if(commonStrings.size()>0) {
                        capTotal = commonStrings.stream().mapToDouble(commonString -> plotMap.get(commonString).getValue()).sum();
                    }
                }

                total = computeHelper.compute(aggregateRequestDto,total,capTotal );
            }


            data.getPlots().add(new Plot(newField, total, dataType));

        } catch (Exception e) {
            // throw new RuntimeException("Computed field configuration not correctly provided");
            logger.error("percentage could not be computed " +e.getMessage());
            data.getPlots().add(new Plot(newField, 0.0, dataType));
        }

    }
    private static boolean doesTextExistInArrayNode(ArrayNode arrayNode, String searchText) {
        for (JsonNode element : arrayNode) {
            if (element.isTextual()) {
                String text = element.asText();
                if (text.equals(searchText)) {
                    return true;
                }
            }
        }
        return false;
    }
}

