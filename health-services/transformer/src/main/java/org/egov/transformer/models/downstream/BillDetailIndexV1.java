package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.transformer.models.bill.BillDetail;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class BillDetailIndexV1 extends ProjectInfo {
    @JsonProperty("id")
    private String id;
    @JsonProperty("billDetail")
    private BillDetail billDetail;
    @JsonProperty("billDetailEdited")
    private Boolean billDetailEdited;
    @JsonProperty("billWfStatusInfo")
    private Map<String, Object> billWfStatusInfo;
    @JsonProperty("wfStatusInfo")
    private Map<String, Object> wfStatusInfo;
    @JsonProperty("userName")
    private String userName;
    @JsonProperty("nameOfUser")
    private String nameOfUser;
    @JsonProperty("role")
    private String role;
    @JsonProperty("boundaryHierarchy")
    private Map<String, String> boundaryHierarchy;
    @JsonProperty("boundaryHierarchyCode")
    private Map<String, String> boundaryHierarchyCode;

}