package org.egov.pgr.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.pgr.config.PGRConfiguration;
import org.egov.pgr.repository.IdGenRepository;
import org.egov.pgr.repository.PGRRepository;
import org.egov.pgr.util.PGRUtils;
import org.egov.pgr.validator.ServiceRequestValidator;
import org.egov.pgr.web.models.RequestSearchCriteria;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchCriteriaScopingTest {

    private static final String CALLER_UUID = "caller-uuid";

    @Mock private PGRUtils utils;
    @Mock private IdGenRepository idGenRepository;
    @Mock private PGRConfiguration config;
    @Mock private UserService userService;
    @Mock private PGRRepository repository;
    @Mock private ServiceRequestValidator validator;

    private EnrichmentService enrichmentService;
    private PGRService pgrService;

    @BeforeEach
    void setup() {
        enrichmentService = new EnrichmentService(utils, idGenRepository, config, userService);
        pgrService = new PGRService(enrichmentService, userService, null, validator, validator, null, config, repository, null);
        when(config.getDefaultLimit()).thenReturn(10);
        when(config.getDefaultOffset()).thenReturn(0);
        when(config.getMaxLimit()).thenReturn(100);
    }

    private static RequestInfo caller(String type) {
        return RequestInfo.builder()
                .userInfo(User.builder().uuid(CALLER_UUID).type(type).tenantId("mz").userName("9999999999").build())
                .build();
    }

    @Test
    void citizenSearchIsLimitedToOwnComplaints() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder()
                .tenantId("mz").mobileNumber("8888888888")
                .userIds(new HashSet<>(Collections.singleton("another-uuid"))).build();

        enrichmentService.enrichSearchRequest(caller("CITIZEN"), criteria);

        assertEquals(Collections.singleton(CALLER_UUID), criteria.getUserIds());
        assertNull(criteria.getMobileNumber());
        verify(userService, never()).enrichUserIds(any(), any());
    }

    @Test
    void citizenSearchWithoutParamsIsLimitedToOwnComplaints() {
        RequestSearchCriteria criteria = new RequestSearchCriteria();

        enrichmentService.enrichSearchRequest(caller("CITIZEN"), criteria);

        assertEquals(Collections.singleton(CALLER_UUID), criteria.getUserIds());
        assertNull(criteria.getMobileNumber());
    }

    @Test
    void employeeSearchIgnoresSuppliedUserIdsAndResolvesMobileNumber() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder()
                .tenantId("mz").mobileNumber("8888888888")
                .userIds(new HashSet<>(Collections.singleton("another-uuid"))).build();
        doAnswer(invocation -> {
            RequestSearchCriteria c = invocation.getArgument(1);
            assertNull(c.getUserIds());
            c.setUserIds(Collections.singleton("resolved-uuid"));
            return null;
        }).when(userService).enrichUserIds(eq("mz"), any());

        enrichmentService.enrichSearchRequest(caller("EMPLOYEE"), criteria);

        assertEquals(Collections.singleton("resolved-uuid"), criteria.getUserIds());
        assertEquals("8888888888", criteria.getMobileNumber());
    }

    @Test
    void employeeSearchWithoutMobileNumberHasNoOwnerFilter() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("mz").build();

        enrichmentService.enrichSearchRequest(caller("EMPLOYEE"), criteria);

        assertNull(criteria.getUserIds());
    }

    @Test
    void citizenCountIsLimitedToOwnComplaints() throws Exception {
        when(repository.getCount(any())).thenReturn(3);
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("mz").build();

        assertEquals(3, pgrService.count(caller("CITIZEN"), criteria));

        ArgumentCaptor<RequestSearchCriteria> captor = ArgumentCaptor.forClass(RequestSearchCriteria.class);
        verify(repository).getCount(captor.capture());
        assertEquals(Collections.singleton(CALLER_UUID), captor.getValue().getUserIds());
    }

    @Test
    void countForUnknownMobileNumberIsZero() throws Exception {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("mz").mobileNumber("8888888888").build();

        assertEquals(0, pgrService.count(caller("EMPLOYEE"), criteria));

        verify(repository, never()).getCount(any());
    }

    @Test
    void countWithoutParamsIsZeroForEmployee() throws Exception {
        assertEquals(0, pgrService.count(caller("EMPLOYEE"), new RequestSearchCriteria()));

        verify(repository, never()).getCount(any());
    }

    @Test
    void plainSearchIsNotAvailableToCitizens() throws Exception {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder()
                .tenantIds(new HashSet<>(Collections.singleton("mz"))).build();

        CustomException ex = assertThrows(CustomException.class, () -> pgrService.plainSearch(caller("CITIZEN"), criteria));

        assertEquals("INVALID_SEARCH", ex.getCode());
        verify(repository, never()).getServiceWrappers(any());
    }

    @Test
    void plainSearchStillServesEmployees() throws Exception {
        when(repository.getServiceWrappers(any())).thenReturn(Collections.emptyList());
        RequestSearchCriteria criteria = RequestSearchCriteria.builder()
                .tenantIds(new HashSet<>(Collections.singleton("mz"))).build();

        assertEquals(0, pgrService.plainSearch(caller("EMPLOYEE"), criteria).size());

        verify(repository).getServiceWrappers(any());
    }
}
