package org.egov.common.models.idgen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;


/**
 * IdRecordRequest
 */
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdRecordBulkRequest {

    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private RequestInfo requestInfo = null;

    @JsonProperty("IdRecords")
    @NotNull
    @Valid
    @Size(min = 1)
    private List<IdRecord> idRecords = new ArrayList<>();

    public org.egov.common.models.idgen.IdRecordBulkRequest addIdRecordItem(IdRecord idRecord) {
        this.idRecords.add(idRecord);
        return this;
    }
}








