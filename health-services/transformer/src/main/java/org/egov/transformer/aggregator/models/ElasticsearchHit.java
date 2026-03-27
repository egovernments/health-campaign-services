package org.egov.transformer.aggregator.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ElasticsearchHit<T> {

  @JsonProperty("_seq_no")
  private long seqNo;

  @JsonProperty("_primary_term")
  private long primaryTerm;

  @JsonProperty("_source")
  private T source;
}
