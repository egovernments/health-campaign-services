package org.egov.common.models.idgen;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Builder
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class IdPoolSearch {

    @JsonProperty("ids")
    @Size(min = 1, message = "idList must contain at least one ID")
    private List<String> idList;

    @JsonProperty("status")
    private IdStatus status;

    @JsonProperty("tenantId")
    @NotNull
    private String tenantId;
}
