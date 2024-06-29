package org.egov.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AttendanceLogSearchCriteria {

    @JsonProperty("ids")
    private List<String> ids;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("registerId")
    private String registerId;

    @JsonProperty("fromTime")
    private BigDecimal fromTime;

    @JsonProperty("toTime")
    private BigDecimal toTime;

    @JsonProperty("individualIds")
    private List<String> individualIds;

    @JsonProperty("status")
    private Status status;

    @JsonProperty("limit")
    private Integer limit;

    @JsonProperty("offset")
    private Integer offset;

    @JsonProperty("sortBy")
    private SortBy sortBy;

    @JsonProperty("sortOrder")
    private SortOrder sortOrder;

    public enum SortOrder {
        ASC,
        DESC
    }

    public enum SortBy {
        lastModifiedTime
    }

}
