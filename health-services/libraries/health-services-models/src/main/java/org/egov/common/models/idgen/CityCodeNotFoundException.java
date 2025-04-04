package org.egov.common.models.idgen;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CityCodeNotFoundException extends RuntimeException {

	private String customMsg;

	private RequestInfo requestInfo;
}
