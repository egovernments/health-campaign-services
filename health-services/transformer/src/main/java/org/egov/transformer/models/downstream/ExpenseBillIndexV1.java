package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.transformer.models.expense.Bill;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExpenseBillIndexV1 {
    @JsonProperty("bill")
    private Bill bill;
    @JsonProperty("boundaryHierarchy")
    private Map<String, String> boundaryHierarchy;
    @JsonProperty("boundaryHierarchyCode")
    private Map<String, String> boundaryHierarchyCode;
    @JsonProperty("totalUsersCount")
    private Integer totalUsersCount;
    @JsonProperty("uniqueUsersCount")
    private Integer uniqueUsersCount;
    @JsonProperty("taskDates")
    private String taskDates;
    @JsonProperty("syncedDate")
    private String syncedDate;
    @JsonProperty("additionalDetails")
    private ObjectNode additionalDetails;

}
