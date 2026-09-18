package org.egov.inbox.web.model.dss;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ResponseData {

        @Valid
        @JsonProperty("chartType")
        private String chartType;

        @Valid
        @JsonProperty("visualizationCode")
        private String visualizationCode;

        @Valid
        @JsonProperty("chartFormat")
        private String chartFormat;

        @Valid
        @JsonProperty("drillDownChartId")
        private String drillDownChartId;

        @Valid
        @JsonProperty("customData")
        private Map<String, Object> customData;

        @Valid
        @JsonProperty("dates")
        private RequestDate dates;

        @Valid
        @JsonProperty("filter")
        private Object filter;

        @Valid
        @JsonProperty("data")
        private List<Data> data = new ArrayList<>();

}
