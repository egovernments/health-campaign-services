package org.egov.inbox.web.model.elasticsearch;

import javax.validation.Valid;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.egov.inbox.web.model.RequestInfo;

;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class InboxElasticSearchRequest {
  @JsonProperty("RequestInfo")
  private RequestInfo RequestInfo;

  @Valid
  @JsonProperty("InboxElasticSearchCriteria")
  private InboxElasticSearchCriteria inboxElasticSearchCriteria ;



}