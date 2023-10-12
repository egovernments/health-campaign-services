package org.egov.stock.util;

import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentRelatedEntity;
import static org.egov.stock.Constants.GET_REQUEST_INFO;
import static org.egov.stock.Constants.NO_PROJECT_FACILITY_MAPPING_EXISTS;
import static org.egov.stock.Constants.STAFF;
import static org.egov.stock.Constants.WAREHOUSE;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.ds.Tuple;
import org.egov.common.models.Error;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.common.service.UserService;
import org.egov.stock.service.FacilityService;
import org.egov.tracer.model.CustomException;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import digit.models.coremodels.UserSearchRequest;

public class ValidatorUtil {

	public static <R, T> Map<T, List<Error>> validateFacilityIds(R request, Map<T, List<Error>> errorDetailsMap,
			List<T> validEntities, String getId, FacilityService facilityService) {

		if (!validEntities.isEmpty()) {
			String tenantId = getTenantId(validEntities);
			Class<?> objClass = getObjClass(validEntities);
			Method idMethod = getMethod(getId, objClass);
			Map<String, T> eMap = getIdToObjMap(validEntities, idMethod);
			RequestInfo requestInfo = (RequestInfo) ReflectionUtils
					.invokeMethod(getMethod(GET_REQUEST_INFO, request.getClass()), request);

			if (!eMap.isEmpty()) {
				List<String> entityIds = new ArrayList<>(eMap.keySet());
				List<String> existingFacilityIds = facilityService.validateFacilityIds(entityIds, validEntities,
						tenantId, errorDetailsMap, requestInfo);
				List<T> invalidEntities = validEntities.stream().filter(notHavingErrors())
						.filter(entity -> !existingFacilityIds
								.contains((String) ReflectionUtils.invokeMethod(idMethod, entity)))
						.collect(Collectors.toList());
				invalidEntities.forEach(entity -> {
					Error error = getErrorForNonExistentRelatedEntity(
							(String) ReflectionUtils.invokeMethod(idMethod, entity));
					populateErrorDetails(entity, error, errorDetailsMap);
				});
			}
		}

		return errorDetailsMap;
	}

	/**
	 * 
	 * Non generic method used for validating sender/receiver (parties) against
	 * facility or staff based on the type
	 * 
	 * @param <R>
	 * @param <T>
	 * @param stockRequest
	 * @param errorDetailsMap
	 * @param validEntities
	 * @param getId
	 * @param facilityService
	 * @return
	 */
	public static <R, T> Map<T, List<Error>> validateStockTransferParties(RequestInfo requestInfo,
			Map<T, List<Error>> errorDetailsMap, List<Stock> validStockEntities, FacilityService facilityService,
			UserService userService) {

		if (!validStockEntities.isEmpty()) {

			Tuple<List<String>, List<String>> tupleOfInvalidStaffIdsAndFacilityIds = validateAndEnrichInvalidPartyIds(
					requestInfo, errorDetailsMap, validStockEntities, facilityService, userService);

			enrichErrorMapFromInvalidPartyIds(errorDetailsMap, validStockEntities,
					tupleOfInvalidStaffIdsAndFacilityIds.getX(), tupleOfInvalidStaffIdsAndFacilityIds.getY());

		}
		return errorDetailsMap;
	}

	/**
	 * validates the list of party-ids (facility and staff) against the respective
	 * APIs and enriches the invalid ids list for both parties
	 * 
	 * @param <T>
	 * @param stockRequest
	 * @param errorDetailsMap
	 * @param validStockEntities
	 * @param facilityService
	 * @param facilityIds
	 * @param InvalidStaffId
	 * @param invalidFacilityIds
	 */
	@SuppressWarnings("unchecked")
	private static <T> Tuple<List<String>, List<String>> validateAndEnrichInvalidPartyIds(RequestInfo requestInfo,
			Map<T, List<Error>> errorDetailsMap, List<Stock> validStockEntities, FacilityService facilityService,
			UserService userService) {

		List<String> facilityIds = new ArrayList<>();
		List<String> staffIds = new ArrayList<>();

		enrichFaciltyAndStaffIdsFromStock(validStockEntities, facilityIds, staffIds);

		// copy all of party identifiers into invalid list
		List<String> invalidStaffIds = new ArrayList<>(staffIds);
		List<String> invalidFacilityIds = new ArrayList<>(facilityIds);

		String tenantId = getTenantId(validStockEntities);

		// validate and remove valid identifiers from invalidStaffIds
		validateStaffIds(userService, requestInfo, staffIds, invalidStaffIds);

		// validate and remove valid identifiers from invalidfacilityIds
		validateFacilityIds(facilityService, facilityIds, validStockEntities, tenantId, errorDetailsMap, requestInfo, invalidFacilityIds);



		return new Tuple<>(invalidStaffIds, invalidFacilityIds);
	}

	/**
	 * private method to fetch the valid facility ids and removed from the invalid facility id list
	 * @param <T>
	 * @param facilityService
	 * @param facilityIds
	 * @param validStockEntities
	 * @param tenantId
	 * @param errorDetailsMap
	 * @param requestInfo
	 * @param invalidFacilityIds
	 */
	private static <T> void validateFacilityIds(FacilityService facilityService, List<String> facilityIds, List<Stock> validStockEntities, String tenantId, Map<T, List<Error>> errorDetailsMap, RequestInfo requestInfo, List<String> invalidFacilityIds) {
		if(CollectionUtils.isEmpty(facilityIds)) return;
		List<String> validFacilityIds = facilityService.validateFacilityIds(facilityIds, (List<T>) validStockEntities,
				tenantId, errorDetailsMap, requestInfo);
		invalidFacilityIds.removeAll(validFacilityIds);
	}

	/**
	 * private method to fetch the valid staff ids and removed from the invalid staff id list
	 * @param userService
	 * @param requestInfo
	 * @param staffIds
	 * @param invalidStaffIds
	 */
	private static void validateStaffIds(UserService userService, RequestInfo requestInfo, List<String> staffIds, List<String> invalidStaffIds) {
		if(CollectionUtils.isEmpty(staffIds)) return;
		UserSearchRequest userSearchRequest = new UserSearchRequest();
		userSearchRequest.setRequestInfo(requestInfo);
		userSearchRequest.setUuid(staffIds);
		List<String> validStaffIds = userService.search(userSearchRequest).stream().map(user -> user.getUuid())
				.collect(Collectors.toList());
		invalidStaffIds.removeAll(validStaffIds);
	}


	/**
	 * Private method to enrich facility id and staff id
	 * 
	 * @param validStockEntities
	 * @param facilityIds
	 * @param staffIds
	 */
	private static void enrichFaciltyAndStaffIdsFromStock(List<Stock> validStockEntities, List<String> facilityIds,
			List<String> staffIds) {

		for (Stock stock : validStockEntities) {

			if (stock.getSenderType().equalsIgnoreCase(WAREHOUSE)) {
				facilityIds.add(stock.getSenderId());
			}
			if (stock.getSenderType().equalsIgnoreCase(STAFF)) {
				staffIds.add(stock.getSenderId());
			}
			if (stock.getReceiverType().equalsIgnoreCase(WAREHOUSE)) {
				facilityIds.add(stock.getReceiverId());
			}
			if (stock.getReceiverType().equalsIgnoreCase(STAFF)) {
				staffIds.add(stock.getReceiverId());
			}
		}
	}

	/**
	 * 
	 * creates the error map from the stock objects with invalid party ids
	 * 
	 * @param errorDetailsMap
	 * @param validStockEntities
	 * @param InvalidStaffId
	 * @param invalidFacilityIds
	 */
	@SuppressWarnings("unchecked")
	private static <T> void enrichErrorMapFromInvalidPartyIds(Map<T, List<Error>> errorDetailsMap,
			List<Stock> validStockEntities, List<String> InvalidStaffId, List<String> invalidFacilityIds) {

		Class<?> objClass = getObjClass(validStockEntities);
		Method senderIdMethod = getMethod("getSenderId", objClass);
		Method recieverIdMethod = getMethod("getReceiverId", objClass);

		for (Stock stock : validStockEntities) {

			String senderId = stock.getSenderId();
			String recieverId = stock.getReceiverId();

			if ((stock.getSenderType().equalsIgnoreCase(WAREHOUSE) && invalidFacilityIds.contains(senderId))

					|| (stock.getSenderType().equalsIgnoreCase(STAFF) && InvalidStaffId.contains(senderId))) {

				getIdForErrorFromMethod(errorDetailsMap, (T) stock, senderIdMethod);
			}

			if ((stock.getReceiverType().equalsIgnoreCase(WAREHOUSE) && invalidFacilityIds.contains(recieverId))

					|| (stock.getReceiverType().equalsIgnoreCase(STAFF) && InvalidStaffId.contains(recieverId))) {

				getIdForErrorFromMethod(errorDetailsMap, (T) stock, recieverIdMethod);
			}
		}
	}

	/**
	 * method to populate error details map
	 * 
	 * @param <T>
	 * @param errorDetailsMap
	 * @param entity
	 * @param idMethod
	 */
	private static <T> void getIdForErrorFromMethod(Map<T, List<Error>> errorDetailsMap, T entity, Method idMethod) {

		Error error = getErrorForNonExistentRelatedEntity((String) ReflectionUtils.invokeMethod(idMethod, entity));
		populateErrorDetails(entity, error, errorDetailsMap);
	}

	/**
	 * 
	 * @param <R>
	 * @param <T>
	 * @param request
	 * @param errorDetailsMap
	 * @param validEntities
	 * @param getId
	 * @param facilityService
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <R, T> Map<T, List<Error>> validateProjectFacilityMappings(R request,
			Map<T, List<Error>> errorDetailsMap, List<T> validEntities, String getReferenceId,
			FacilityService facilityService) {

		if (!validEntities.isEmpty()) {

			String tenantId = getTenantId(validEntities);
			Class<?> objClass = getObjClass(validEntities);
			Method idMethod = getMethod(getReferenceId, objClass);
			RequestInfo requestInfo = (RequestInfo) ReflectionUtils
					.invokeMethod(getMethod(GET_REQUEST_INFO, request.getClass()), request);

			Map<String, List<String>> ProjectFacilityMappingOfIds = facilityService
					.validateProjectFacilityMappings((List<T>) validEntities, tenantId, errorDetailsMap, requestInfo);

			List<T> invalidStocks = new ArrayList<>();

			if (validEntities.get(0) instanceof Stock)
				enrichErrorForStock((List<Stock>) validEntities, ProjectFacilityMappingOfIds, invalidStocks,
						errorDetailsMap);
			else if (validEntities.get(0) instanceof StockReconciliation)
				enrichErrorForStockReconciliation((List<StockReconciliation>) validEntities,
						ProjectFacilityMappingOfIds, invalidStocks, errorDetailsMap);

		}

		return errorDetailsMap;
	}

	@SuppressWarnings("unchecked")
	private static <T> void enrichErrorForStock(List<Stock> validEntities,
			Map<String, List<String>> ProjectFacilityMappingOfIds, List<T> invalidStocks,
			Map<T, List<Error>> errorDetailsMap) {

		for (Stock stock : validEntities) {

			String senderId = stock.getSenderId();
			String receiverId = stock.getReceiverId();

			List<String> facilityIds = ProjectFacilityMappingOfIds.get(stock.getReferenceId());
			if (!CollectionUtils.isEmpty(facilityIds)) {

				if (stock.getSenderType().equalsIgnoreCase("WAREHOUSE") && !facilityIds.contains(senderId)) {
					populateErrorForStock(stock, senderId, errorDetailsMap);
				}

				if (stock.getReceiverType().equalsIgnoreCase("WAREHOUSE") && !facilityIds.contains(receiverId))
					populateErrorForStock(stock, receiverId, errorDetailsMap);
			} else {
				populateErrorForStock(stock, senderId + " and " + receiverId, errorDetailsMap);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> void populateErrorForStock(Stock stock, String facilityId, Map<T, List<Error>> errorDetailsMap) {

		String errorMessage = String.format("No mapping exists for project id: %s & facility id: %s",
				stock.getReferenceId(), facilityId);

		Error error = Error.builder().errorMessage(errorMessage).errorCode(NO_PROJECT_FACILITY_MAPPING_EXISTS)
				.type(Error.ErrorType.NON_RECOVERABLE)
				.exception(new CustomException(NO_PROJECT_FACILITY_MAPPING_EXISTS, errorMessage)).build();
		populateErrorDetails((T) stock, error, errorDetailsMap);
	}

	@SuppressWarnings("unchecked")
	private static <T> void enrichErrorForStockReconciliation(List<StockReconciliation> validEntities,
			Map<String, List<String>> ProjectFacilityMappingOfIds, List<T> invalidStocks,
			Map<T, List<Error>> errorDetailsMap) {

		for (StockReconciliation stockReconciliation : validEntities) {

			List<String> facilityIds = ProjectFacilityMappingOfIds.get(stockReconciliation.getReferenceId());
			if (CollectionUtils.isEmpty(facilityIds)) {
				populateErrorForStockReconciliation(stockReconciliation, errorDetailsMap);
			} else if (!facilityIds.contains(stockReconciliation.getFacilityId()))
				populateErrorForStockReconciliation(stockReconciliation, errorDetailsMap);
		}
	}
	
	@SuppressWarnings("unchecked")
	private static <T> void populateErrorForStockReconciliation(StockReconciliation stockReconciliation,
			Map<T, List<Error>> errorDetailsMap) {

		String errorMessage = String.format("No mapping exists for project id: %s & facility id: %s",
				stockReconciliation.getReferenceId(), stockReconciliation.getFacilityId());

		Error error = Error.builder().errorMessage(errorMessage).errorCode(NO_PROJECT_FACILITY_MAPPING_EXISTS)
				.type(Error.ErrorType.NON_RECOVERABLE)
				.exception(new CustomException(NO_PROJECT_FACILITY_MAPPING_EXISTS, errorMessage)).build();
		populateErrorDetails((T) stockReconciliation, error, errorDetailsMap);
	}
}
