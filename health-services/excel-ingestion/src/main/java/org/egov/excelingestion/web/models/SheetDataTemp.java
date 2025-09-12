package org.egov.excelingestion.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SheetDataTemp {

    @JsonProperty("referenceId")
    private String referenceId;

    @JsonProperty("fileStoreId")
    private String fileStoreId;

    @JsonProperty("sheetName")
    private String sheetName;

    @JsonProperty("rowNumber")
    private Integer rowNumber;

    @JsonProperty("rowJson")
    private Map<String, Object> rowJson;

    @JsonProperty("createdBy")
    private String createdBy;

    @JsonProperty("createdTime")
    private Long createdTime;

    @JsonProperty("deleteTime")
    private Long deleteTime;
}