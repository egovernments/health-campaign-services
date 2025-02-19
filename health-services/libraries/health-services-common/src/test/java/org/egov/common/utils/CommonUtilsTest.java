package org.egov.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.models.AuditDetails;
import lombok.Builder;
import lombok.Data;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.error.handler.ErrorHandler;
import org.egov.common.helper.AuditDetailsTestBuilder;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.helpers.OtherObject;
import org.egov.common.helpers.SomeObject;
import org.egov.common.helpers.SomeObjectWithClientRefId;
import org.egov.common.helpers.SomeValidator;
import org.egov.common.models.ApiDetails;
import org.egov.common.models.Error;
import org.egov.common.models.ErrorDetails;
import org.egov.common.validator.Validator;
import org.egov.tracer.ExceptionAdvise;
import org.egov.tracer.model.CustomException;
import org.egov.tracer.model.ErrorDetail;
import org.egov.tracer.model.ErrorEntity;
import org.egov.tracer.model.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.commons.util.ReflectionUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommonUtilsTest {

    @Mock
    SomeValidator someValidator;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("should check if the request is for update")
    void shouldCheckIfTheRequestIsForUpdate() {
        SomeRequest someRequest = SomeRequest.builder().apiOperation(SomeEnum.UPDATE).build();

        assertTrue(CommonUtils.isForUpdate(someRequest));
    }

    @Test
    @DisplayName("should handle for null api operation")
    void shouldHandleForNullForNullApiOperation() {
        SomeRequest someRequest = SomeRequest.builder().apiOperation(null).build();

        assertFalse(CommonUtils.isForUpdate(someRequest));
    }

    @Test
    @DisplayName("should throw custom exception if invalid object is passed for update")
    void shouldThrowCustomExceptionIfInvalidObjectIsPassedForUpdate() {
        List<String> list = new ArrayList<>();

        assertThrows(CustomException.class, () -> CommonUtils.isForUpdate(list));
    }

    @Test
    @DisplayName("should check if the request is for Delete")
    void shouldCheckIfTheRequestIsForDelete() {
        SomeRequest someRequest = SomeRequest.builder().apiOperation(SomeEnum.DELETE).build();

        assertTrue(CommonUtils.isForDelete(someRequest));
    }

    @Test
    @DisplayName("should check if the request is for create")
    void shouldCheckIfTheRequestIsForCreate() {
        SomeRequest someRequest = SomeRequest.builder().apiOperation(SomeEnum.CREATE).build();

        assertTrue(CommonUtils.isForCreate(someRequest));
    }

    @Test
    @DisplayName("should check if the request is for create when api operation is null")
    void shouldCheckIfTheRequestIsForCreateWhenApiOperationIsNull() {
        SomeRequest someRequest = SomeRequest.builder().apiOperation(null).build();

        assertFalse(CommonUtils.isForCreate(someRequest));
    }

    @Test
    @DisplayName("should create a set of given attribute")
    void shouldCreateASetOfGivenAttribute() {
        SomeObject someObject = SomeObject.builder().id("some-id-1").build();
        SomeObject otherSomeObject = SomeObject.builder().id("some-id-2").build();
        List<SomeObject> objects = new ArrayList<>();
        objects.add(someObject);
        objects.add(otherSomeObject);

        assertEquals(2, CommonUtils.getSet(objects, "getId").size());
    }

    @Test
    @DisplayName("should get the difference of lists when one list is sublist of another")
    void shouldGetTheDifferenceOfListsWhenOneListIsSublistOfAnother() {
        List<String> idList = new ArrayList<>();
        idList.add("some-id");
        idList.add("bad-id");
        List<String> otherIdList = new ArrayList<>();
        otherIdList.add("some-id");

        assertEquals(1, CommonUtils.getDifference(idList, otherIdList).size());
    }

    @Test
    @DisplayName("should get the difference of lists when both the lists have same number of items")
    void shouldGetTheDifferenceOfListsWhenBothTheListsHaveSameNumberOfItems() {
        List<String> idList = new ArrayList<>();
        idList.add("some-id");
        idList.add("other-id");
        List<String> otherIdList = new ArrayList<>();
        otherIdList.add("some-id");
        otherIdList.add("other-id");

        assertEquals(0, CommonUtils.getDifference(idList, otherIdList).size());
    }

//    @Test
//    @DisplayName("should validate the ids as per the given validator")
//    void shouldValidateTheIdsAsPerTheGivenValidator() {
//        Set<String> idSet = new HashSet<>();
//        idSet.add("some-id");
//        idSet.add("other-id");
//        UnaryOperator<List<String>> validator = UnaryOperator.identity();
//
//        assertDoesNotThrow(() -> CommonUtils.validateIds(idSet, validator));
//    }
//
//    @Test
//    @DisplayName("should throw exception in case an invalid id is found")
//    void shouldThrowExceptionInCaseAnInvalidIdIsFound() {
//        Set<String> idSet = new HashSet<>();
//        idSet.add("some-id");
//        idSet.add("other-id");
//        UnaryOperator<List<String>> validator = (idList) -> {
//            idList.remove(0);
//            return idList;
//        };
//
//        assertDoesNotThrow(() -> CommonUtils.validateIds(idSet, validator));
//    }

    @Test
    @DisplayName("should get audit details for create")
    void shouldGetAuditDetailsForCreate() {
        RequestInfo requestInfo = RequestInfoTestBuilder.builder()
                .withCompleteRequestInfo().build();

        AuditDetails auditDetails = CommonUtils.getAuditDetailsForCreate(requestInfo);

        assertEquals(auditDetails.getCreatedTime(), auditDetails.getLastModifiedTime());
        assertEquals(auditDetails.getCreatedBy(), auditDetails.getLastModifiedBy());
        assertTrue(auditDetails.getCreatedTime() != null
                && auditDetails.getLastModifiedTime() != null
                && auditDetails.getCreatedBy() != null
                && auditDetails.getLastModifiedBy() != null);
    }

    @Test
    @DisplayName("should get audit details for update")
    void shouldGetAuditDetailsForUpdate() {
        RequestInfo requestInfo = RequestInfoTestBuilder.builder()
                .withCompleteRequestInfo().build();
        AuditDetails existingAuditDetails = CommonUtils.getAuditDetailsForCreate(requestInfo);

        RequestInfo otherRequestInfo = RequestInfoTestBuilder.builder()
                .withCompleteRequestInfo().build();
        otherRequestInfo.getUserInfo().setUuid("other-uuid");
        AuditDetails auditDetails = CommonUtils.getAuditDetailsForUpdate(existingAuditDetails,
                otherRequestInfo.getUserInfo().getUuid());

        assertEquals(auditDetails.getCreatedTime(), existingAuditDetails.getCreatedTime());
        assertEquals(auditDetails.getCreatedBy(), existingAuditDetails.getCreatedBy());
        assertEquals(auditDetails.getLastModifiedBy(), existingAuditDetails.getLastModifiedBy());
        assertTrue(auditDetails.getCreatedTime() != null
                && auditDetails.getLastModifiedTime() != null
                && auditDetails.getCreatedBy() != null
                && auditDetails.getLastModifiedBy() != null);
    }

    @Test
    @DisplayName("should return true if search is by id only")
    void shouldReturnTrueIfSearchIsByIdOnly() {
        SomeObject someObject = SomeObject.builder()
                .id("some-id")
                .build();

        assertTrue(CommonUtils.isSearchByIdOnly(someObject));
    }

    @Test
    @DisplayName("should return false if search is not by id only")
    void shouldReturnFalseIfSearchIsNotByIdOnly() {
        SomeObject someObject = SomeObject.builder()
                .id("some-id")
                .otherField("other-field")
                .build();

        assertFalse(CommonUtils.isSearchByIdOnly(someObject));
    }

    @Test
    @DisplayName("should return true if search is by clientReferenceId only")
    void shouldReturnTrueIfSearchIsByClientReferenceIdOnly() {
        SomeObjectWithClientRefId someObject = SomeObjectWithClientRefId.builder()
                .clientReferenceId("some-id")
                .build();

        assertTrue(CommonUtils.isSearchByIdOnly(someObject, "clientReferenceId"));
    }

    @Test
    @DisplayName("should return false if search is not by clientReferenceId only")
    void shouldReturnFalseIfSearchIsNotByClientReferenceIdOnly() {
        SomeObjectWithClientRefId someObject = SomeObjectWithClientRefId.builder()
                .clientReferenceId("some-id")
                .otherField("other-field")
                .build();

        assertFalse(CommonUtils.isSearchByIdOnly(someObject, "clientReferenceId"));
    }

    @Test
    @DisplayName("should check row version")
    void shouldCheckRowVersion() {
        SomeObject someObject = SomeObject.builder()
                .id("some-id")
                .rowVersion(1)
                .build();
        Map<String, SomeObject> idToObjMap = new HashMap<>();
        idToObjMap.put(someObject.getId(), someObject);
        SomeObject otherObject = SomeObject.builder()
                .id("some-id")
                .rowVersion(1)
                .build();
        List<SomeObject> objList = new ArrayList<>();
        objList.add(otherObject);

        assertDoesNotThrow(() -> CommonUtils.checkRowVersion(idToObjMap, objList));
    }

    @Test
    @DisplayName("should return request object with mismatched row version")
    void shouldReturnRequestObjectWithMismatchedRowVersion() {
        SomeObject someObject = SomeObject.builder()
                .id("some-id")
                .rowVersion(1)
                .build();

        SomeObject someOtherObject = SomeObject.builder()
                .id("some-other-id")
                .rowVersion(1)
                .build();
        Map<String, SomeObject> idToObjMap = new HashMap<>();
        idToObjMap.put(someObject.getId(), someObject);
        idToObjMap.put(someOtherObject.getId(), someOtherObject);
        SomeObject otherObject = SomeObject.builder()
                .id("some-id")
                .rowVersion(1)
                .build();
        SomeObject otherInvalidObject = SomeObject.builder()
                .id("some-other-id")
                .rowVersion(2)
                .build();
        List<SomeObject> objList = new ArrayList<>();
        objList.add(otherObject);
        objList.add(otherInvalidObject);

        Method idMethod = getMethod("getId", SomeObject.class);

        assertEquals("some-other-id",
                CommonUtils.getEntitiesWithMismatchedRowVersion(idToObjMap, objList, idMethod).get(0).getId());
    }

    @Test
    @DisplayName("should throw exception if row versions mismatch")
    void shouldThrowExceptionIfRowVersionsMismatch() {
        SomeObject someObject = SomeObject.builder()
                .id("some-id")
                .rowVersion(1)
                .build();
        Map<String, SomeObject> idToObjMap = new HashMap<>();
        idToObjMap.put(someObject.getId(), someObject);
        SomeObject otherObject = SomeObject.builder()
                .id("some-id")
                .rowVersion(2)
                .build();
        List<SomeObject> objList = new ArrayList<>();
        objList.add(otherObject);

        assertThrows(CustomException.class, () -> CommonUtils.checkRowVersion(idToObjMap, objList));
    }

    @Test
    @DisplayName("should fetch tenantId")
    void shouldFetchTenantId() {
        List<SomeObject> objList = new ArrayList<>();
        SomeObject someObject = SomeObject.builder()
                .tenantId("some-tenant-id")
                .build();
        objList.add(someObject);

        assertEquals("some-tenant-id", CommonUtils.getTenantId(objList));
    }

    @Test
    @DisplayName("should enrich with audit details and id")
    void shouldEnrichWithAuditDetailsAndId() {
        RequestInfo requestInfo = RequestInfoTestBuilder.builder()
                .withCompleteRequestInfo().build();
        List<String> idList = new ArrayList<>();
        idList.add("some-id");
        SomeObject someObject = SomeObject.builder().otherField("other-field").build();
        List<SomeObject> objList = new ArrayList<>();
        objList.add(someObject);

        CommonUtils.enrichForCreate(objList, idList, requestInfo);

        assertNotNull(objList.stream().findAny().get().getAuditDetails());
    }

    @Test
    @DisplayName("should create id to obj map")
    void shouldCreateIdToObjMap() {
        SomeObject someObject = SomeObject.builder()
                .id("some-id")
                .rowVersion(2)
                .build();
        List<SomeObject> objList = new ArrayList<>();
        objList.add(someObject);

        assertEquals(someObject, CommonUtils.getIdToObjMap(objList).get("some-id"));
    }

    @Test
    @DisplayName("should return null for null id")
    void shouldReturnNullForNullId() {
        SomeObject someObject = SomeObject.builder()
                .id(null)
                .rowVersion(2)
                .build();
        List<SomeObject> objList = new ArrayList<>();
        objList.add(someObject);

        assertNull(CommonUtils.getIdToObjMap(objList).get("some-id"));
    }

    @Test
    @DisplayName("should validate entity")
    void shouldValidateEntity() {
        Map<String, SomeObject> idToObjInRequestMap = new HashMap<>();
        List<SomeObject> objInDbList = new ArrayList<>();
        SomeObject someObject = SomeObject.builder()
                .id("some-id")
                .rowVersion(2)
                .build();
        idToObjInRequestMap.put("some-id", someObject);
        objInDbList.add(someObject);

        assertDoesNotThrow(() -> CommonUtils.validateEntities(idToObjInRequestMap, objInDbList));
    }

    @Test
    @DisplayName("should throw exception if there are invalid entities")
    void shouldThrowExceptionIfThereAreInvalidEntities() {
        Map<String, SomeObject> idToObjInRequestMap = new HashMap<>();
        List<SomeObject> objInDbList = new ArrayList<>();
        SomeObject someObject = SomeObject.builder()
                .id("some-id")
                .rowVersion(2)
                .build();
        idToObjInRequestMap.put("some-id", someObject);

        assertThrows(CustomException.class,
                () -> CommonUtils.validateEntities(idToObjInRequestMap, objInDbList));
    }

    @Test
    @DisplayName("should enrich for update")
    void shouldEnrichForUpdate() {
        Map<String, SomeObject> idToObjInRequestMap = new HashMap<>();
        List<SomeObject> objInDbList = new ArrayList<>();
        SomeObject someObject = SomeObject.builder()
                .id("some-id")
                .rowVersion(2)
                .build();
        idToObjInRequestMap.put("some-id", someObject);
        SomeObject otherObject = SomeObject.builder()
                .id("some-id")
                .rowVersion(2)
                .auditDetails(AuditDetailsTestBuilder.builder()
                        .withAuditDetails()
                        .build())
                .build();
        objInDbList.add(otherObject);
        SomeRequest someRequest = SomeRequest.builder().apiOperation(SomeEnum.DELETE)
                .requestInfo(RequestInfoTestBuilder.builder()
                        .withCompleteRequestInfo()
                .build())
                .build();

        CommonUtils.enrichForUpdate(idToObjInRequestMap, objInDbList, someRequest);

        assertEquals(idToObjInRequestMap.get("some-id").getRowVersion(),
                objInDbList.get(0).getRowVersion() + 1);
        assertTrue(idToObjInRequestMap.get("some-id").getIsDeleted());
    }

    @Test
    @DisplayName("should throw exception if Ids are null")
    void shouldThrowExceptionIfIdsAreNull() {
        List<SomeObject> objList = new ArrayList<>();
        SomeObject someObject = SomeObject.builder()
                .id("some-id")
                .rowVersion(2)
                .build();
        SomeObject someOtherObject = SomeObject.builder()
                .id(null)
                .rowVersion(2)
                .build();
        objList.add(someObject);
        objList.add(someOtherObject);

        assertThrows(CustomException.class, () -> CommonUtils.identifyNullIds(objList));
    }

    @Test
    @DisplayName("should filter by lastModifiedTime")
    void shouldFilterByLastModifiedTime() {
        SomeObject someObject = SomeObject.builder()
                .auditDetails(AuditDetailsTestBuilder.builder()
                        .withAuditDetails()
                        .build())
                .build();
        List<SomeObject> objList = new ArrayList<>();
        objList.add(someObject);
        Long lastChangedSince = Instant.now().minusMillis(5000).toEpochMilli();
        assertTrue(objList.stream().anyMatch(CommonUtils.lastChangedSince(lastChangedSince)));
    }

    @Test
    @DisplayName("should filter by isDeleted")
    void shouldFilterByIsDeleted() {
        SomeObject someObject = SomeObject.builder().isDeleted(Boolean.TRUE).build();
        List<SomeObject> objList = new ArrayList<>();
        objList.add(someObject);
        assertTrue(objList.stream().anyMatch(CommonUtils.includeDeleted(Boolean.TRUE)));
    }

    @Test
    @DisplayName("should filter by tenantId")
    void shouldFilterByTenantId() {
        SomeObject someObject = SomeObject.builder().tenantId("some-tenant-id").build();
        List<SomeObject> objList = new ArrayList<>();
        objList.add(someObject);
        assertTrue(objList.stream().anyMatch(CommonUtils.havingTenantId("some-tenant-id")));
    }

    @Test
    @DisplayName("should enrich with id")
    void shouldEnrichWithId() {
        List<String> idList = new ArrayList<>();
        idList.add("some-id");
        SomeObject someObject = SomeObject.builder().otherField("other-field").build();
        List<SomeObject> objList = new ArrayList<>();
        objList.add(someObject);

        CommonUtils.enrichId(objList, idList);

        assertNotNull(objList.stream().findAny().get().getId());
    }

    @Test
    @DisplayName("should return id field if clientReferenceId field not present")
    void shouldReturnIdIfClientRefIdNotPresent() {
        SomeObject someObject = SomeObject.builder().id("some-id").tenantId("some-tenant-id").build();
        List<SomeObject> objList = new ArrayList<>();
        objList.add(someObject);

        Method getId = CommonUtils.getIdMethod(objList);

        assertTrue(getId.toString().endsWith("getId()"));
    }

    @Test
    @DisplayName("should return id field if clientReferenceId field is null")
    void shouldReturnIdIfClientRefIdIsNull() {
        SomeObjectWithClientRefId someObject =SomeObjectWithClientRefId.builder()
                .id("some-id").tenantId("some-tenant-id").build();
        List<SomeObjectWithClientRefId> objList = new ArrayList<>();
        objList.add(someObject);

        Method getId = CommonUtils.getIdMethod(objList);

        assertTrue(getId.toString().endsWith("getId()"));
    }

    @Test
    @DisplayName("should not return clientReferenceId field if clientReferenceId field is not null and id is present")
    void shouldNotReturnClientRefIdIfNotNullAndIdIsPresent() {
        SomeObjectWithClientRefId someObject =SomeObjectWithClientRefId.builder()
                .id("some-id").clientReferenceId("some-id").tenantId("some-tenant-id").build();
        List<SomeObjectWithClientRefId> objList = new ArrayList<>();
        objList.add(someObject);

        Method getId = CommonUtils.getIdMethod(objList);

        assertFalse(getId.toString().endsWith("getClientReferenceId()"));
    }

    @Test
    @DisplayName("should get the name of the clientReferenceId field if available and not null")
    void shouldGetTheNameOfTheClientReferenceIdFieldIfAvailableAndNotNull() {
        SomeObjectWithClientRefId someObject = SomeObjectWithClientRefId.builder()
                .clientReferenceId("some-client-reference-id").build();
        assertEquals("clientReferenceId", CommonUtils.getIdFieldName(someObject));
    }

    @Test
    @DisplayName("should get the name of the id field if available and not null")
    void shouldGetTheNameOfIdFieldIfAvailableAndNotNull() {
        SomeObjectWithClientRefId someObject = SomeObjectWithClientRefId.builder()
                .id("some-id").build();
        assertEquals("id", CommonUtils.getIdFieldName(someObject));
    }

    @Test
    @DisplayName("should get a list of other objects from a list of some objects")
    void shouldGetAListOfOtherObjectsFromAListOfSomeObjects() {
        List<SomeObject> someObjects = new ArrayList<>();
        someObjects.add(SomeObject.builder()
                        .otherField("some-other-field")
                        .otherObject(Arrays.asList(OtherObject.builder()
                                        .someOtherField("some-other-field").build(),
                                OtherObject.builder()
                                        .someOtherField("some-other-field").build()))
                .build());
        someObjects.add(SomeObject.builder()
                .otherField("some-other-field")
                .otherObject(Arrays.asList(OtherObject.builder()
                                .someOtherField("some-other-field").build(),
                        OtherObject.builder()
                                .someOtherField("some-other-field").build()))
                .build());

        assertEquals(4, CommonUtils.collectFromList(someObjects,
                        SomeObject::getOtherObject).size());
    }

    @Test
    @DisplayName("should supply specified number of uuids")
    void shouldSupplySpecifiedNumberOfUuids() {
        assertEquals(5, CommonUtils.uuidSupplier().apply(5).size());
    }

    @Test
    @DisplayName("should get id field name from method")
    void shouldGetIdFieldNameFromMethod() {
        SomeObjectWithClientRefId someObject = SomeObjectWithClientRefId.builder()
                .clientReferenceId("some-client-reference-id").build();
        assertEquals("clientReferenceId", CommonUtils.getIdFieldName(getMethod("getClientReferenceId", someObject.getClass())));
    }

    @Test
    @DisplayName("enrich ids from existing entities when key is id")
    void shouldEnrichIdsFromExistingEntitiesWhenKeyIsId() {
        List<SomeObjectWithClientRefId> existingEntities = new ArrayList<>();
        existingEntities.add(SomeObjectWithClientRefId.builder()
                        .id("some-id")
                .clientReferenceId("some-client-reference-id").build());
        List<SomeObjectWithClientRefId> entitiesToUpdate = new ArrayList<>();
        entitiesToUpdate.add(SomeObjectWithClientRefId.builder()
                .id("some-id")
                .build());
        Method idMethod = CommonUtils.getIdMethod(entitiesToUpdate);
        Map<String, SomeObjectWithClientRefId> idToObjMap = CommonUtils.getIdToObjMap(entitiesToUpdate, idMethod);
        CommonUtils.enrichIdsFromExistingEntities(idToObjMap, existingEntities, idMethod);
        assertEquals("some-client-reference-id",
                entitiesToUpdate.stream().findFirst().get().getClientReferenceId());
        assertEquals("some-id",
                entitiesToUpdate.stream().findFirst().get().getId());
    }

    @Test
    @DisplayName("enrich ids from existing entities when key is clientReferenceId")
    void shouldEnrichIdsFromExistingEntitiesWhenKeyIsClientReferenceId() {
        List<SomeObjectWithClientRefId> existingEntities = new ArrayList<>();
        existingEntities.add(SomeObjectWithClientRefId.builder()
                .id("some-id")
                .clientReferenceId("some-client-reference-id").build());
        List<SomeObjectWithClientRefId> entitiesToUpdate = new ArrayList<>();
        entitiesToUpdate.add(SomeObjectWithClientRefId.builder()
                .clientReferenceId("some-client-reference-id")
                .build());
        Method idMethod = CommonUtils.getIdMethod(entitiesToUpdate);
        Map<String, SomeObjectWithClientRefId> idToObjMap = CommonUtils.getIdToObjMap(entitiesToUpdate, idMethod);
        CommonUtils.enrichIdsFromExistingEntities(idToObjMap, existingEntities, idMethod);
        assertEquals("some-client-reference-id",
                entitiesToUpdate.stream().findFirst().get().getClientReferenceId());
        assertEquals("some-id",
                entitiesToUpdate.stream().findFirst().get().getId());
    }

    @Test
    @DisplayName("enrich ids from existing entities when both id and clientReferenceId are present")
    void shouldEnrichIdsFromExistingEntitiesWhenBothIdAndClientReferenceIdArePresent() {
        List<SomeObjectWithClientRefId> existingEntities = new ArrayList<>();
        existingEntities.add(SomeObjectWithClientRefId.builder()
                .id("some-id")
                .clientReferenceId("some-client-reference-id").build());
        List<SomeObjectWithClientRefId> entitiesToUpdate = new ArrayList<>();
        entitiesToUpdate.add(SomeObjectWithClientRefId.builder()
                .clientReferenceId("some-client-reference-id")
                        .id("some-id")
                .build());
        Method idMethod = CommonUtils.getIdMethod(entitiesToUpdate);
        Map<String, SomeObjectWithClientRefId> idToObjMap = CommonUtils.getIdToObjMap(entitiesToUpdate, idMethod);
        CommonUtils.enrichIdsFromExistingEntities(idToObjMap, existingEntities, idMethod);
        assertEquals("some-client-reference-id",
                entitiesToUpdate.stream().findFirst().get().getClientReferenceId());
        assertEquals("some-id",
                entitiesToUpdate.stream().findFirst().get().getId());
    }

    @Test
    @DisplayName("should be able to get id method based on a field name")
    void shouldBeAbleToGetIdMethodBasedOnAFieldName() {
        List<SomeObjectWithClientRefId> objList = new ArrayList<>();
        objList.add(SomeObjectWithClientRefId.builder()
                .id("some-id")
                .otherClientReferenceId("some-other-client-reference-id").build());
        Method idMethod = CommonUtils.getIdMethod(objList, "otherClientReferenceId");
        assertEquals("some-other-client-reference-id", ReflectionUtils.invokeMethod(idMethod,
                objList.get(0)));
    }

    @Test
    @DisplayName("should be able to get id method based on available field name")
    void shouldBeAbleToGetIdMethodBasedOnAvailableFieldName() {
        List<SomeObjectWithClientRefId> objList = new ArrayList<>();
        objList.add(SomeObjectWithClientRefId.builder()
                .id("some-id")
                .otherClientReferenceId("some-other-client-reference-id").build());
        Method idMethod = CommonUtils.getIdMethod(objList, "id", "otherClientReferenceId");
        assertEquals("some-id", ReflectionUtils.invokeMethod(idMethod,
                objList.get(0)));
    }

    @Test
    @DisplayName("should enrich with audit details and is delete")
    void shouldEnrichWithAuditDetailsAndIsDelete() {
        RequestInfo requestInfo = RequestInfoTestBuilder.builder()
                .withCompleteRequestInfo().build();
        SomeObject someObject = SomeObject.builder().otherField("other-field")
                .requestInfo(requestInfo).build();
        List<SomeObject> objList = new ArrayList<>();
        objList.add(someObject);

        CommonUtils.enrichForDelete(objList, someObject.getRequestInfo(), false);

        assertNotNull(objList.stream().findAny().get().getAuditDetails());
        assertTrue(objList.stream().findAny().get().getIsDeleted());
    }
    @Test
    @DisplayName("should call validate method")
    void shouldCallValidateMethod() {
        List<Validator<SomeObject, OtherObject>> validators = new ArrayList<>();
        validators.add(someValidator);
        Predicate<Validator<SomeObject, OtherObject>> isApplicableForTest = validator -> true;
        RequestInfo requestInfo = RequestInfoTestBuilder.builder()
                .withCompleteRequestInfo().build();
        SomeObject someObject = SomeObject.builder().otherField("other-field")
                .requestInfo(requestInfo).build();
        List<SomeObject> objList = new ArrayList<>();
        objList.add(someObject);

        when(someValidator.validate(any())).thenReturn(Collections.emptyMap());
        CommonUtils.validate(validators, isApplicableForTest, someObject, "setOtherObject");

        verify(someValidator, times(1)).validate(any());
    }

    @Test
    @DisplayName("should populate error details map")
    void shouldPopulateErrorDetailsMap() {
        RequestInfo requestInfo = RequestInfoTestBuilder.builder()
                .withCompleteRequestInfo().build();
        SomeObject someObject = SomeObject.builder().otherField("other-field")
                .requestInfo(requestInfo).build();
        OtherObject otherObject = OtherObject.builder().someOtherField("some").build();
        Map<OtherObject, ErrorDetails> errorDetailsMap = new HashMap<>();
        Map<OtherObject, List<Error>> errors = new HashMap<>();
        errors.put(otherObject, Arrays.asList(Error.builder().errorCode("SOMECODE").build()));

        CommonUtils.populateErrorDetails(someObject, errorDetailsMap, errors, "setOtherObject");

        assertEquals(errorDetailsMap.size(), 1);
    }

    @Test
    @DisplayName("should populate error details map for custom exception")
    void shouldPopulateErrorDetailsMapForCustomException() {
        RequestInfo requestInfo = RequestInfoTestBuilder.builder()
                .withCompleteRequestInfo().build();
        SomeObject someObject = SomeObject.builder().otherField("other-field")
                .requestInfo(requestInfo).build();
        OtherObject otherObject = OtherObject.builder().someOtherField("some").build();
        List<OtherObject> validPayloads = Arrays.asList(otherObject);
        Map<OtherObject, ErrorDetails> errorDetailsMap = new HashMap<>();
        Map<OtherObject, List<Error>> errors = new HashMap<>();
        errors.put(otherObject, Arrays.asList(Error.builder().errorCode("SOMECODE").build()));
        CustomException exception = new CustomException("IDGEN_ERROR", "some error in ID gen");
        CommonUtils.populateErrorDetails(someObject, errorDetailsMap, validPayloads, exception, "setOtherObject");

        assertEquals(errorDetailsMap.size(), 1);
        assertEquals(errorDetailsMap.get(otherObject).getErrors().get(0).getType(), Error.ErrorType.NON_RECOVERABLE);
    }

    @Test
    @DisplayName("should call exceptionHandler with correct model")
    void shouldCallExceptionHandlerWithCorrectModel() throws JsonProcessingException {
        Map<SomeRequest, ErrorDetails> errorDetailsMap = new HashMap<>();
        Exception ex = new Exception();
        SomeRequest someRequest = SomeRequest.builder()
                .requestInfo(RequestInfo.builder()
                        .authToken("some-token")
                        .build())
                .apiOperation(SomeEnum.CREATE)
                .build();
        errorDetailsMap.put(someRequest, ErrorDetails.builder()
                        .apiDetails(ApiDetails.builder()
                                .url("some-url")
                                .contentType("application/json")
                                .requestBody(new ObjectMapper().writeValueAsString(someRequest))
                                .build())
                        .errors(Arrays.asList(Error.builder()
                                        .exception(ex)
                                        .type(Error.ErrorType.RECOVERABLE)
                                        .errorCode("some-error-code")
                                        .errorMessage("some-error-message")
                                .build()))
                .build());
        List<ErrorDetail> expected = new ArrayList<>();
                expected.add(ErrorDetail.builder()
                        .errors(Arrays.asList(ErrorEntity.builder()
                                        .exception(ex)
                                        .errorType(ErrorType.RECOVERABLE)
                                        .errorCode("some-error-code")
                                        .errorMessage("some-error-message")
                                .build()))
                        .apiDetails(org.egov.tracer.model.ApiDetails.builder()
                                .url("some-url")
                                .contentType("application/json")
                                .requestBody(new ObjectMapper().writeValueAsString(someRequest))
                                .build())
                .build());
        ErrorHandler.exceptionAdviseInstance = Mockito.mock(ExceptionAdvise.class);

        CommonUtils.handleErrors(errorDetailsMap, true, "some-error-code");

        ArgumentCaptor<List<ErrorDetail>> argument = ArgumentCaptor.forClass(List.class);
        verify(ErrorHandler.exceptionAdviseInstance, times(1))
                .exceptionHandler(argument.capture());
        List<ErrorDetail> actual = argument.getValue();
        assertEquals(expected.toString(), actual.toString());

    }

    @Test
    @DisplayName("should throw custom exception when isBulk flag is false")
    void shouldCallCustomExceptionWhenIsBulkFlagIsFalse() throws JsonProcessingException {
        Map<SomeRequest, ErrorDetails> errorDetailsMap = new HashMap<>();
        Exception ex = new Exception();
        SomeRequest someRequest = SomeRequest.builder()
                .requestInfo(RequestInfo.builder()
                        .authToken("some-token")
                        .build())
                .apiOperation(SomeEnum.CREATE)
                .build();
        errorDetailsMap.put(someRequest, ErrorDetails.builder()
                .apiDetails(ApiDetails.builder()
                        .url("some-url")
                        .contentType("application/json")
                        .requestBody(new ObjectMapper().writeValueAsString(someRequest))
                        .build())
                .errors(Arrays.asList(Error.builder()
                        .exception(ex)
                        .type(Error.ErrorType.RECOVERABLE)
                        .errorCode("some-error-code")
                        .errorMessage("some-error-message")
                        .build()))
                .build());

        try {
            CommonUtils.handleErrors(errorDetailsMap, false, "some-error-code");
        } catch (CustomException e) {
            assertEquals(e.getCode(), "some-error-code");
        }

    }

    @Data
    @Builder
    public static class SomeRequest {
        private SomeEnum apiOperation;
        private RequestInfo requestInfo;
    }

    enum SomeEnum {
        CREATE, UPDATE, DELETE;
    }
}