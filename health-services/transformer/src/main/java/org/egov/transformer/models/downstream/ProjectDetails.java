package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * The project fields an index needs that {@link ProjectInfo} does not carry. Cached in Redis with a short
 * TTL rather than in memory, so it is shared across pods and cannot go stale for long.
 *
 * <p>The Redis serializer writes the fully qualified class name into the value (default typing is enabled),
 * so renaming or moving this class invalidates existing entries. They fail to deserialize and are treated
 * as misses, which the short TTL clears out anyway.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectDetails {

    public static final ProjectDetails EMPTY = new ProjectDetails(null, null, Collections.emptyList());

    @JsonProperty("localityCode")
    private String localityCode;

    @JsonProperty("additionalDetails")
    private JsonNode additionalDetails;

    @JsonProperty("taskDates")
    private List<String> taskDates;
}
