package org.egov.common.models.idgen;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.egov.common.contract.request.RequestInfo;

/**
 * <h1>IDSeqNotFoundException</h1>
 * 
 * @author Pavan Kumar kamma
 *
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class IDSeqNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private String customMsg;

	private RequestInfo requestInfo;

}
