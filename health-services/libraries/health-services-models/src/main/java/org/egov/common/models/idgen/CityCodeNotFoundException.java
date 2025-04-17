package org.egov.common.models.idgen;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.egov.common.contract.request.RequestInfo;

@Getter
@Setter
@AllArgsConstructor
public class CityCodeNotFoundException extends RuntimeException {

	private String customMsg;

	private RequestInfo requestInfo;
}
