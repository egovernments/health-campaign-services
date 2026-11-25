package org.egov.household.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.error.handler.ErrorHandler;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.household.Address;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.models.household.HouseholdRequest;
import org.egov.common.pii.EncryptionDecryptionUtil;
import org.egov.common.service.IdGenService;
import org.egov.common.validator.Validator;
import org.egov.household.config.HouseholdConfiguration;
import org.egov.household.helper.FakeEncService;
import org.egov.household.repository.HouseholdRepository;
import org.egov.household.validators.household.HAddressValueValidator;
import org.egov.tracer.ExceptionAdvise;
import org.egov.tracer.model.CustomException;
import org.egov.tracer.model.ErrorDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The household API input must not carry cipher-shaped address values: create and update, single and bulk (consumer),
 * run HAddressValueValidator, with the real validator in the service's chain.
 */
class HouseholdServiceAddressValueTest {

    private static final String CREATE_TOPIC = "save-household-topic";
    private static final String UPDATE_TOPIC = "update-household-topic";
    /** keyId|base64 of 24 bytes: the shape enc-service returns. */
    private static final String CIPHER = "123456|" + Base64.getEncoder().encodeToString(new byte[24]);

    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setTimeZone(TimeZone.getTimeZone("UTC"));
    private final RequestInfo requestInfo = RequestInfoTestBuilder.builder().withCompleteRequestInfo().build();
    private final ExceptionAdvise exceptionAdvise = mock(ExceptionAdvise.class);
    private ExceptionAdvise previousAdvise;
    private HouseholdRepository repository;
    private HouseholdService service;

    @BeforeEach
    void setUp() {
        previousAdvise = ErrorHandler.exceptionAdviseInstance;
        ErrorHandler.exceptionAdviseInstance = exceptionAdvise;
        repository = mock(HouseholdRepository.class);
        HouseholdConfiguration configuration = new HouseholdConfiguration();
        configuration.setCreateTopic(CREATE_TOPIC);
        configuration.setUpdateTopic(UPDATE_TOPIC);
        configuration.setPiiEncryptModel("HouseholdPiiEncrypt");
        configuration.setPiiDecryptModel("HouseholdPiiDecrypt");
        EncryptionDecryptionUtil encUtil = FakeEncService.encUtil(mapper);
        HouseholdPiiCryptoService crypto = new HouseholdPiiCryptoService(encUtil, mapper, configuration);
        List<Validator<HouseholdBulkRequest, Household>> validators = new ArrayList<>();
        validators.add(new HAddressValueValidator());
        service = new HouseholdService(repository, mock(IdGenService.class), configuration, validators,
                mock(HouseholdEnrichmentService.class), crypto);
    }

    @AfterEach
    void tearDown() {
        ErrorHandler.exceptionAdviseInstance = previousAdvise;
    }

    private static Household household(String id, String doorNo) {
        return Household.builder().id(id).clientReferenceId("cr-" + id).tenantId("default").memberCount(3).rowVersion(1)
                .isDeleted(false)
                .address(Address.builder().id("a-" + id).tenantId("default").doorNo(doorNo).city("Maputo").build())
                .build();
    }

    private HouseholdBulkRequest bulkRequest(Household... households) {
        return HouseholdBulkRequest.builder().requestInfo(requestInfo).households(new ArrayList<>(List.of(households))).build();
    }

    private static void assertNoValueIn(String text) {
        assertTrue(text.contains("HOUSEHOLD_ADDRESS_VALUE_NOT_ALLOWED"), text);
        assertFalse(text.contains(CIPHER), "the value is in: " + text);
        assertFalse(text.contains("AAAAAAAA"), "part of the value is in: " + text);
    }

    // ------------------------------------------------------------------ sync API

    @Test
    @DisplayName("create (single API): a cipher-shaped address value is rejected, the error has the code and not the value, nothing is saved")
    void createRejectsCipherShapedValue() {
        HouseholdRequest request = HouseholdRequest.builder().requestInfo(requestInfo).household(household("h-1", CIPHER)).build();

        CustomException e = assertThrows(CustomException.class, () -> service.create(request));

        assertNoValueIn(e.getMessage());
        verify(repository, never()).save(anyList(), any());
    }

    @Test
    @DisplayName("delete (single API): a cipher-shaped address value is rejected too, nothing is saved or cached")
    void deleteRejectsCipherShapedValue() {
        HouseholdRequest request = HouseholdRequest.builder().requestInfo(requestInfo).household(household("h-1", CIPHER)).build();

        CustomException e = assertThrows(CustomException.class, () -> service.delete(request));

        assertNoValueIn(e.getMessage());
        verify(repository, never()).save(anyList(), any());
        verify(repository, never()).putInCache(anyList());
    }

    @Test
    @DisplayName("update (single API): same")
    void updateRejectsCipherShapedValue() {
        HouseholdRequest request = HouseholdRequest.builder().requestInfo(requestInfo).household(household("h-1", CIPHER)).build();

        CustomException e = assertThrows(CustomException.class, () -> service.update(request));

        assertNoValueIn(e.getMessage());
        verify(repository, never()).save(anyList(), any());
    }

    @Test
    @DisplayName("create and update (single API): a plain value is accepted and saved")
    void plainValueIsAcceptedAndSaved() {
        service.create(HouseholdRequest.builder().requestInfo(requestInfo).household(household("h-1", "12B")).build());
        service.update(HouseholdRequest.builder().requestInfo(requestInfo).household(household("h-1", "12B")).build());

        verify(repository).save(anyList(), eq(CREATE_TOPIC));
        verify(repository).save(anyList(), eq(UPDATE_TOPIC));
    }

    @Test
    @DisplayName("create and update (single API): a plain value with a '|' that is not cipher-shaped is accepted and saved")
    void plainValueWithPipeIsAcceptedAndSaved() {
        service.create(HouseholdRequest.builder().requestInfo(requestInfo).household(household("h-1", "Flat 4|Block B")).build());
        service.update(HouseholdRequest.builder().requestInfo(requestInfo).household(household("h-1", "12|B")).build());

        verify(repository).save(anyList(), eq(CREATE_TOPIC));
        verify(repository).save(anyList(), eq(UPDATE_TOPIC));
    }

    // ------------------------------------------------------------------ bulk (consumer)

    @SuppressWarnings("unchecked")
    private List<ErrorDetail> reportedErrors() {
        ArgumentCaptor<List<ErrorDetail>> captor = ArgumentCaptor.forClass(List.class);
        verify(exceptionAdvise).exceptionHandler(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<Household> saved(String topic) {
        ArgumentCaptor<List<Household>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).save(captor.capture(), eq(topic));
        return captor.getValue();
    }

    @Test
    @DisplayName("bulk create (consumer): the cipher-shaped record gets a per-record error without the value; the other record is saved")
    void bulkCreateRejectsOnlyTheBadRecord() {
        service.createFromQueue(bulkRequest(household("bad", CIPHER), household("good", "12B")));

        List<ErrorDetail> errors = reportedErrors();
        assertEquals(1, errors.size());
        assertEquals("HOUSEHOLD_ADDRESS_VALUE_NOT_ALLOWED", errors.get(0).getErrors().get(0).getErrorCode());
        assertNoValueIn(errors.get(0).toString());
        List<Household> savedHouseholds = saved(CREATE_TOPIC);
        assertEquals(1, savedHouseholds.size());
        assertEquals("good", savedHouseholds.get(0).getId());
    }

    @Test
    @DisplayName("bulk update (consumer): same")
    void bulkUpdateRejectsOnlyTheBadRecord() {
        service.updateFromQueue(bulkRequest(household("bad", CIPHER), household("good", "12B")));

        List<ErrorDetail> errors = reportedErrors();
        assertEquals(1, errors.size());
        assertEquals("HOUSEHOLD_ADDRESS_VALUE_NOT_ALLOWED", errors.get(0).getErrors().get(0).getErrorCode());
        assertNoValueIn(errors.get(0).toString());
        List<Household> savedHouseholds = saved(UPDATE_TOPIC);
        assertEquals(1, savedHouseholds.size());
        assertEquals("good", savedHouseholds.get(0).getId());
    }

    // ------------------------------------------------------------------ cache (the bulk API caches before the consumer validates)

    @Test
    @DisplayName("putInCache: a household with a cipher-shaped value is not cached; the others are")
    void putInCacheSkipsCipherShapedInput() {
        service.putInCache(List.of(household("bad", CIPHER), household("good", "12B")));

        ArgumentCaptor<List<Household>> cached = ArgumentCaptor.forClass(List.class);
        verify(repository).putInCache(cached.capture());
        assertEquals(1, cached.getValue().size());
        assertEquals("good", cached.getValue().get(0).getId());
    }
}
