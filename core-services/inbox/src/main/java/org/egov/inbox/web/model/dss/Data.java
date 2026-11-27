package org.egov.inbox.web.model.dss;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
public class Data {

    @Valid
    @JsonProperty("headerName")
    private String headerName;

    @Valid
    @JsonProperty("headerValue")
    private BigDecimal headerValue;

    @Valid
    @JsonProperty("headerSymbol")
    private String headerSymbol;

    @Valid
    @JsonProperty("insight")
    private InsightsWidget insight;

    private List<Plot> plots = new ArrayList<>();

    public Data(String name, BigDecimal value, String symbol) {
        this.headerName = name;
        this.headerValue = value;
        this.headerSymbol = symbol;
    }

    public Data(String name, BigDecimal value, String symbol, List<Plot> plots) {
        this.headerName = name;
        this.headerValue = value;
        this.headerSymbol = symbol;
        this.plots = plots;
    }
}
