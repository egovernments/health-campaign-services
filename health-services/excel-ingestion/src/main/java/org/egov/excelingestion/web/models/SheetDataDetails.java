package org.egov.excelingestion.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SheetDataDetails {

    @JsonProperty("Data")
    private List<Map<String, Object>> data;

    @JsonProperty("TotalCount")
    private Integer totalCount;

    @JsonProperty("SheetWiseCounts")
    private List<Map<String, Object>> sheetWiseCounts;
}