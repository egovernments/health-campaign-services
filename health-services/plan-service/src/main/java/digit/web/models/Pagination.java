package digit.web.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pagination
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Pagination {

    @JsonIgnore
    private String sortBy;

    @JsonIgnore
    private String sortOrder;

    @JsonProperty("limit")
    @Min(1)
    @Max(50)
    private Integer limit;

    @JsonProperty("offset")
    @Min(0)
    private Integer offset;
}