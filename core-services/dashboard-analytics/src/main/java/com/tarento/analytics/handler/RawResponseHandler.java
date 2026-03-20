package com.tarento.analytics.handler;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.analytics.dto.AggregateDto;
import com.tarento.analytics.dto.AggregateRequestDto;
import com.tarento.analytics.enums.ChartType;
import org.springframework.stereotype.Component;

@Component
public class RawResponseHandler implements IResponseHandler {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Override
	public AggregateDto translate(AggregateRequestDto request, ObjectNode aggregations) throws IOException {
		AggregateDto dto = new AggregateDto();
		dto.setChartType(ChartType.RAW_RESPONSE);
		dto.setVisualizationCode(request.getVisualizationCode());
		Map<String, Object> raw = MAPPER.convertValue(aggregations, Map.class);
		dto.setCustomData(Collections.singletonMap("rawResponse", raw));
		return dto;
	}
}
