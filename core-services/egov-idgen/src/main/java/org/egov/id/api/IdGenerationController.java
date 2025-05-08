package org.egov.id.api;

import io.swagger.annotations.ApiParam;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.models.idgen.*;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.id.config.PropertiesManager;
import org.egov.id.producer.IdGenProducer;
import org.egov.id.service.IdDispatchService;
import org.egov.id.service.IdGenerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * api's related to the IdGeneration Controller
 * 
 * @author Pavan Kumar Kamma
 * @author Sreejith K
 */
@RestController
@RequestMapping(path = "/id/")
public class IdGenerationController {

	@Autowired
	IdGenerationService idGenerationService;

	@Autowired
	IdDispatchService idDispatchService;

	@Autowired
	HttpServletRequest servletRequest;

	@Autowired
	PropertiesManager propertiesManager;

	@Autowired
	IdGenProducer producer;
	/**
	 * description: generate unique ID for property
	 * 
	 * @param idGenerationRequest
	 * @return IdGenerationResponse
	 * @throws Exception
	 */
	@RequestMapping(method = RequestMethod.POST, path = "_generate")
	public IdGenerationResponse generateIdResponse(
			@RequestBody @Valid IdGenerationRequest idGenerationRequest)
			throws Exception {

		IdGenerationResponse idGenerationResponse = idGenerationService
				.generateIdResponse(idGenerationRequest);

		return idGenerationResponse;
	}

	/**
	 * description: generate unique ID for property
	 *
	 * @param request
	 * @return IDPoolGenerationResponse
	 * @throws Exception
	 */

	@RequestMapping(method = RequestMethod.POST, path = "id_pool/_generate")
	public IDPoolGenerationResponse generateIDs(@RequestBody @Valid IDPoolGenerationRequest request) throws Exception {
		IDPoolGenerationResponse idPoolGenerationResponse = idGenerationService.generateIDPool(request);
		return idPoolGenerationResponse;
	}

	/**
	 * description: generate unique ID for property
	 *
	 * @param request
	 * @return IdDispatchResponse
	 * @throws Exception
	 */

	@RequestMapping(method = RequestMethod.POST, path = "id_pool/_dispatch")
	public IdDispatchResponse dispatchIds(@RequestBody @Valid IdDispatchRequest request)  throws Exception {
			IdDispatchResponse response = idDispatchService.dispatchIds( request);
			return response;
	}

	/**
	 * description: generate unique ID for property
	 *
	 * @param IdPoolSearchRequest
	 * @return IdDispatchResponse
	 * @throws Exception
	 */

	@RequestMapping(method = RequestMethod.POST, path = "id_pool/_search")
	public IdDispatchResponse searchGeneratedIDs(@RequestBody @Valid IdPoolSearchRequest request)  throws Exception {
		return idDispatchService.searchIds(request.getRequestInfo(), request.getIdPoolSearch());
	}

	/**
	 * description: generate unique ID for property
	 *
	 * @param IdRecordBulkRequest
	 * @return  ResponseEntity
	 * @throws Exception
	 */
	@RequestMapping(value = "id_pool/_update", method = RequestMethod.POST)
	public ResponseEntity<ResponseInfo> idRecordUpdate(@ApiParam(value = "Details for the IdRecord.", required = true) @Valid @RequestBody IdRecordBulkRequest request, @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) {
		request.getRequestInfo().setApiId(servletRequest.getRequestURI());
		producer.push(propertiesManager.getBulkIdUpdateTopic(), request);

		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
				.createResponseInfo(request.getRequestInfo(), true));
	}

}
