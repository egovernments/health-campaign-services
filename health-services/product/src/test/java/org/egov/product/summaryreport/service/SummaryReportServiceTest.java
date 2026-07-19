package org.egov.product.summaryreport.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.product.summaryreport.config.SummaryReportConfiguration;
import org.egov.product.summaryreport.repository.SummaryReportRepository;
import org.egov.product.summaryreport.web.models.DailyReportSummary;
import org.egov.product.summaryreport.web.models.SummaryReportSearchCriteria;
import org.egov.product.summaryreport.web.models.SummaryReportSearchRequest;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummaryReportServiceTest {

    @InjectMocks
    private SummaryReportService summaryReportService;

    @Mock
    private SummaryReportRepository repository;

    @Mock
    private SummaryReportConfiguration config;

    @Captor
    private ArgumentCaptor<Long> startCaptor;

    @Captor
    private ArgumentCaptor<Long> endCaptor;

    private static final String UUID = "some-uuid";
    private static final String TENANT = "default";
    private static final Long START = 1_000L;
    private static final Long END = 9_000L;

    @BeforeEach
    void setUp() {
        // Tenant timezone used for day-boundary normalization. UTC keeps the math simple.
        lenient().when(config.getTimezoneForTenant(anyString())).thenReturn("UTC");
        // Default all repo calls to empty; individual tests override what they need.
        lenient().when(repository.householdsRegisteredByDay(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(new HashMap<>());
        lenient().when(repository.individualRegisteredByDay(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(new HashMap<>());
        lenient().when(repository.beneficiariesRegisteredByDay(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(new HashMap<>());
        lenient().when(repository.childrenTreatedByDay(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(new HashMap<>());
        lenient().when(repository.stockConsumedByDay(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(new HashMap<>());
    }

    private SummaryReportSearchRequest request(RequestInfo requestInfo, SummaryReportSearchCriteria criteria) {
        return SummaryReportSearchRequest.builder()
                .requestInfo(requestInfo)
                .summaryReportSearchCriteria(criteria)
                .build();
    }

    private SummaryReportSearchCriteria validCriteria() {
        return SummaryReportSearchCriteria.builder()
                .tenantId(TENANT).startDate(START).endDate(END).build();
    }

    private RequestInfo completeRequestInfo() {
        return RequestInfoTestBuilder.builder().withCompleteRequestInfo().build();
    }

    @Test
    @DisplayName("should merge all five metrics by day, ordered ascending, defaulting missing metrics to 0")
    void shouldMergeMetricsByDay() {
        Map<String, Long> households = new HashMap<>();
        households.put("2026-07-17", 5L);
        households.put("2026-07-18", 3L);
        when(repository.householdsRegisteredByDay(eq(UUID), eq(TENANT), anyLong(), anyLong())).thenReturn(households);

        when(repository.childrenTreatedByDay(eq(UUID), eq(TENANT), anyLong(), anyLong()))
                .thenReturn(Collections.singletonMap("2026-07-18", 7L));

        Map<String, Long> stockDay = new LinkedHashMap<>();
        stockDay.put("pv-1", 10L);
        stockDay.put("pv-2", 4L);
        when(repository.stockConsumedByDay(eq(UUID), eq(TENANT), anyLong(), anyLong()))
                .thenReturn(Collections.singletonMap("2026-07-17", stockDay));

        List<DailyReportSummary> result =
                summaryReportService.getDailySummary(request(completeRequestInfo(), validCriteria()));

        assertEquals(2, result.size());

        DailyReportSummary d17 = result.get(0);
        assertEquals("2026-07-17", d17.getDate());
        assertEquals(UUID, d17.getCreatedBy());
        assertEquals(5L, d17.getHouseholdsRegistered());
        assertEquals(0L, d17.getChildrenTreated());
        assertEquals(0L, d17.getIndividualRegistered());
        assertEquals(2, d17.getStockConsumedMap().size());
        assertEquals(10L, d17.getStockConsumedMap().get("pv-1"));

        DailyReportSummary d18 = result.get(1);
        assertEquals("2026-07-18", d18.getDate());
        assertEquals(3L, d18.getHouseholdsRegistered());
        assertEquals(7L, d18.getChildrenTreated());
        assertTrue(d18.getStockConsumedMap().isEmpty());
    }

    @Test
    @DisplayName("should return empty list when no data exists for the employee")
    void shouldReturnEmptyListWhenNoData() {
        List<DailyReportSummary> result =
                summaryReportService.getDailySummary(request(completeRequestInfo(), validCriteria()));
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("should resolve employee from RequestInfo.userInfo.uuid")
    void shouldUseUuidFromRequestInfo() {
        when(repository.householdsRegisteredByDay(eq(UUID), eq(TENANT), anyLong(), anyLong()))
                .thenReturn(Collections.singletonMap("2026-07-18", 1L));

        List<DailyReportSummary> result =
                summaryReportService.getDailySummary(request(completeRequestInfo(), validCriteria()));

        assertEquals(1, result.size());
        assertEquals(UUID, result.get(0).getCreatedBy());
    }

    @Test
    @DisplayName("mid-day start/end are normalized to full local days (start-of-day .. end-of-day)")
    void shouldNormalizeToFullDays() {
        ZoneId utc = ZoneId.of("UTC");
        // 2026-07-17 12:30:00 UTC and 2026-07-19 08:15:00 UTC (both mid-day)
        long midStart = Instant.parse("2026-07-17T12:30:00Z").toEpochMilli();
        long midEnd = Instant.parse("2026-07-19T08:15:00Z").toEpochMilli();
        SummaryReportSearchCriteria criteria = SummaryReportSearchCriteria.builder()
                .tenantId(TENANT).startDate(midStart).endDate(midEnd).build();

        summaryReportService.getDailySummary(request(completeRequestInfo(), criteria));

        verify(repository).householdsRegisteredByDay(eq(UUID), eq(TENANT),
                startCaptor.capture(), endCaptor.capture());

        long expectedStart = Instant.parse("2026-07-17T00:00:00Z").toEpochMilli();
        long expectedEnd = Instant.parse("2026-07-20T00:00:00Z").toEpochMilli() - 1; // 2026-07-19 23:59:59.999
        assertEquals(expectedStart, startCaptor.getValue(), "startDate should snap to start of day");
        assertEquals(expectedEnd, endCaptor.getValue(), "endDate should snap to end of day");
    }

    @Test
    @DisplayName("normalization uses the tenant's timezone, not a fixed zone")
    void shouldNormalizePerTenantZone() {
        // Tenant in UTC+14; the setUp default (UTC) still applies to other tenants.
        when(config.getTimezoneForTenant("kir")).thenReturn("Pacific/Kiritimati");
        long midday = Instant.parse("2026-07-17T12:30:00Z").toEpochMilli(); // 2026-07-18 02:30 local (+14)
        SummaryReportSearchCriteria criteria = SummaryReportSearchCriteria.builder()
                .tenantId("kir").startDate(midday).endDate(midday).build();

        summaryReportService.getDailySummary(request(completeRequestInfo(), criteria));

        verify(repository).householdsRegisteredByDay(eq(UUID), eq("kir"),
                startCaptor.capture(), endCaptor.capture());

        ZoneId kir = ZoneId.of("Pacific/Kiritimati");
        long expStart = Instant.ofEpochMilli(midday).atZone(kir).toLocalDate()
                .atStartOfDay(kir).toInstant().toEpochMilli();
        long expEnd = Instant.ofEpochMilli(midday).atZone(kir).toLocalDate()
                .plusDays(1).atStartOfDay(kir).toInstant().toEpochMilli() - 1;
        assertEquals(expStart, startCaptor.getValue(), "start should snap to start of the tenant-local day");
        assertEquals(expEnd, endCaptor.getValue(), "end should snap to end of the tenant-local day");
        // Must differ from a UTC-based normalization -> proves the tenant zone is used
        long utcStart = Instant.parse("2026-07-17T00:00:00Z").toEpochMilli();
        assertTrue(startCaptor.getValue() != utcStart, "tenant tz must drive normalization, not UTC");
    }

    @Test
    @DisplayName("should throw when userInfo uuid is missing")
    void shouldThrowWhenUuidMissing() {
        RequestInfo noUser = RequestInfoTestBuilder.builder().build();
        CustomException ex = assertThrows(CustomException.class,
                () -> summaryReportService.getDailySummary(request(noUser, validCriteria())));
        assertEquals("INVALID_USER", ex.getCode());
    }

    @Test
    @DisplayName("should throw when startDate is after endDate")
    void shouldThrowWhenStartAfterEnd() {
        SummaryReportSearchCriteria criteria = SummaryReportSearchCriteria.builder()
                .tenantId(TENANT).startDate(END).endDate(START).build();
        CustomException ex = assertThrows(CustomException.class,
                () -> summaryReportService.getDailySummary(request(completeRequestInfo(), criteria)));
        assertEquals("INVALID_DATE_RANGE", ex.getCode());
    }

    @Test
    @DisplayName("should throw when date range is missing")
    void shouldThrowWhenDatesMissing() {
        SummaryReportSearchCriteria criteria = SummaryReportSearchCriteria.builder()
                .tenantId(TENANT).startDate(null).endDate(END).build();
        CustomException ex = assertThrows(CustomException.class,
                () -> summaryReportService.getDailySummary(request(completeRequestInfo(), criteria)));
        assertEquals("INVALID_DATE_RANGE", ex.getCode());
    }

    @Test
    @DisplayName("should throw when tenantId is missing")
    void shouldThrowWhenTenantMissing() {
        SummaryReportSearchCriteria criteria = SummaryReportSearchCriteria.builder()
                .tenantId(null).startDate(START).endDate(END).build();
        CustomException ex = assertThrows(CustomException.class,
                () -> summaryReportService.getDailySummary(request(completeRequestInfo(), criteria)));
        assertEquals("INVALID_TENANT", ex.getCode());
    }

    @Test
    @DisplayName("should throw when criteria is null")
    void shouldThrowWhenCriteriaNull() {
        CustomException ex = assertThrows(CustomException.class,
                () -> summaryReportService.getDailySummary(request(completeRequestInfo(), null)));
        assertEquals("INVALID_CRITERIA", ex.getCode());
    }
}
