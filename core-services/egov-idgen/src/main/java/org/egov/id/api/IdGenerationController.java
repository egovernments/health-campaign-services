package org.egov.id.api;

import jakarta.validation.Valid;
import org.egov.common.models.idgen.*;
import org.egov.id.service.IdDispatchService;
import org.egov.id.service.IdGenerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;


/**
 * api's related to the IdGeneration Controller
 * 
 * @author Pavan Kumar Kamma
 */
@RestController
@RequestMapping(path = "/id/")
public class IdGenerationController {

	@Autowired
	IdGenerationService idGenerationService;

	@Autowired
	IdDispatchService idDispatchService;


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
	public IDPoolGenerationResponse generateIDs(@RequestBody IDPoolGenerationRequest request) throws Exception {
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

	@RequestMapping(method = RequestMethod.POST, path = "id_pool/dispatch")
	public IdDispatchResponse dispatchIds(@RequestBody IdDispatchRequest request)  throws Exception {
			IdDispatchResponse response = idDispatchService.dispatchIds( request);
			return response;
	}

	@RequestMapping(method = RequestMethod.POST, path = "id_pool/search")
	public IdDispatchResponse searchGeneratedIDs(@RequestBody @Valid IdPoolSearchRequest request)  throws Exception {
		return idDispatchService.searchIds(request.getRequestInfo(), request.getIdPoolSearch());
	}

}
