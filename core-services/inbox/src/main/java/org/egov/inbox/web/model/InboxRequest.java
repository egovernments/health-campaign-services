package org.egov.inbox.web.model;


import org.egov.common.contract.request.RequestInfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;;

@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class InboxRequest   {
  @JsonProperty("RequestInfo")
  private RequestInfo RequestInfo;

  @Valid
  @JsonProperty("inbox")
  private InboxSearchCriteria inbox ;



}