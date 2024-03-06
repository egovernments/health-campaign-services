package digit.web.models;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import javax.validation.Valid;
import javax.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

/**
 * Target
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2024-03-04T09:55:29.782094600+05:30[Asia/Calcutta]")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Target {
    @JsonProperty("id")
    @Valid
    private UUID id = null;

    @JsonProperty("taskId")
    @Valid
    private UUID taskId = null;

    @JsonProperty("metric")
    private String metric = null;

    @JsonProperty("metricDetail")
    private Object metricDetail = null;

    @JsonProperty("activityCode")
    private String activityCode = null;

}
