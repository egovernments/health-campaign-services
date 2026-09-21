package org.egov.excelingestion.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.repository.ServiceRequestRepository;
import org.egov.excelingestion.web.models.CampaignSearchResponse;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Which campaign's stored data pre-fills a generated sheet. The clone case is what makes a clone's
 * template carry the parent's users and facilities WITH row ids, so the immutable-join check can
 * reject edits to them — the behaviour the borrowed parent workbook could never provide.
 */
@ExtendWith(MockitoExtension.class)
class CloneDataSourceResolutionTest {

    private static final String TENANT = "dev";
    private static final String CLONE_ID = "clone-uuid";
    private static final String CLONE_NUMBER = "CMP-2026-09-16-007858";
    private static final String PARENT_ID = "parent-uuid";
    private static final String PARENT_NUMBER = "CMP-2026-09-02-007816";

    @Mock private ServiceRequestRepository serviceRequestRepository;
    @Mock private ExcelIngestionConfig config;
    @Mock private ApiPayloadBuilder apiPayloadBuilder;
    @Mock private CustomExceptionHandler exceptionHandler;

    private CampaignService service;
    private final RequestInfo requestInfo = new RequestInfo();

    @BeforeEach
    void setUp() {
        service = spy(new CampaignService(serviceRequestRepository, config, apiPayloadBuilder, exceptionHandler));
    }

    private static CampaignSearchResponse.CampaignDetail campaign(String id, String number, String clonedCampaignId) {
        CampaignSearchResponse.AdditionalDetails details = new CampaignSearchResponse.AdditionalDetails();
        details.setClonedCampaignId(clonedCampaignId);
        return CampaignSearchResponse.CampaignDetail.builder()
                .id(id).tenantId(TENANT).campaignNumber(number).additionalDetails(details).build();
    }

    private static List<Map<String, Object>> oneRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("data", new HashMap<>());
        return Collections.singletonList(row);
    }

    @Test
    void nonCloneUsesItsOwnNumberWithoutCheckingData() {
        doReturn(campaign(CLONE_ID, CLONE_NUMBER, null)).when(service).searchCampaignById(CLONE_ID, TENANT, requestInfo);

        assertEquals(CLONE_NUMBER, service.resolveDataSourceCampaignNumber(CLONE_ID, "user", TENANT, requestInfo));
        verify(service, never()).searchCampaignDataByType(any(), any(), any(), any(), any());
    }

    @Test
    void flagsTheClonePathSoGeneratorsCanFailClosed() {
        doReturn(campaign(CLONE_ID, CLONE_NUMBER, PARENT_ID)).when(service).searchCampaignById(CLONE_ID, TENANT, requestInfo);
        doReturn(Collections.emptyList()).when(service).searchCampaignDataByType("user", null, CLONE_NUMBER, TENANT, requestInfo);
        doReturn(campaign(PARENT_ID, PARENT_NUMBER, null)).when(service).searchCampaignById(PARENT_ID, TENANT, requestInfo);

        CampaignService.DataSource fromParent = service.resolveDataSource(CLONE_ID, "user", TENANT, requestInfo);
        assertEquals(PARENT_NUMBER, fromParent.getCampaignNumber());
        assertEquals(true, fromParent.isFromCloneSource());

        CampaignService.DataSource own = service.resolveDataSource(PARENT_ID, "user", TENANT, requestInfo);
        assertEquals(PARENT_NUMBER, own.getCampaignNumber());
        assertEquals(false, own.isFromCloneSource());
    }

    @Test
    void cloneThatOwnsNoUserDataReadsTheParent() {
        doReturn(campaign(CLONE_ID, CLONE_NUMBER, PARENT_ID)).when(service).searchCampaignById(CLONE_ID, TENANT, requestInfo);
        doReturn(Collections.emptyList()).when(service).searchCampaignDataByType("user", null, CLONE_NUMBER, TENANT, requestInfo);
        doReturn(campaign(PARENT_ID, PARENT_NUMBER, null)).when(service).searchCampaignById(PARENT_ID, TENANT, requestInfo);

        assertEquals(PARENT_NUMBER, service.resolveDataSourceCampaignNumber(CLONE_ID, "user", TENANT, requestInfo));
    }

    @Test
    void cloneThatAlreadyOwnsDataKeepsItsOwn_soDeletionsStick() {
        doReturn(campaign(CLONE_ID, CLONE_NUMBER, PARENT_ID)).when(service).searchCampaignById(CLONE_ID, TENANT, requestInfo);
        doReturn(oneRow()).when(service).searchCampaignDataByType("facility", null, CLONE_NUMBER, TENANT, requestInfo);

        assertEquals(CLONE_NUMBER, service.resolveDataSourceCampaignNumber(CLONE_ID, "facility", TENANT, requestInfo));
        verify(service, never()).searchCampaignById(eq(PARENT_ID), any(), any());
    }

    @Test
    void perTypeDecision_userFromParentWhileFacilityIsOwn() {
        doReturn(campaign(CLONE_ID, CLONE_NUMBER, PARENT_ID)).when(service).searchCampaignById(CLONE_ID, TENANT, requestInfo);
        doReturn(oneRow()).when(service).searchCampaignDataByType("facility", null, CLONE_NUMBER, TENANT, requestInfo);
        doReturn(Collections.emptyList()).when(service).searchCampaignDataByType("user", null, CLONE_NUMBER, TENANT, requestInfo);
        doReturn(campaign(PARENT_ID, PARENT_NUMBER, null)).when(service).searchCampaignById(PARENT_ID, TENANT, requestInfo);

        assertEquals(CLONE_NUMBER, service.resolveDataSourceCampaignNumber(CLONE_ID, "facility", TENANT, requestInfo));
        assertEquals(PARENT_NUMBER, service.resolveDataSourceCampaignNumber(CLONE_ID, "user", TENANT, requestInfo));
    }

    @Test
    void selfReferencingLineageIsIgnored() {
        doReturn(campaign(CLONE_ID, CLONE_NUMBER, CLONE_ID)).when(service).searchCampaignById(CLONE_ID, TENANT, requestInfo);

        assertEquals(CLONE_NUMBER, service.resolveDataSourceCampaignNumber(CLONE_ID, "boundary", TENANT, requestInfo));
        verify(service, never()).searchCampaignDataByType(any(), any(), any(), any(), any());
    }

    @Test
    void unreadableOwnDataCheckFallsBackToOwn_neverResurrectsFromParent() {
        doReturn(campaign(CLONE_ID, CLONE_NUMBER, PARENT_ID)).when(service).searchCampaignById(CLONE_ID, TENANT, requestInfo);
        doThrow(new CustomException("CAMPAIGN_DATA_SEARCH_ERROR", "boom"))
                .when(service).searchCampaignDataByType("user", null, CLONE_NUMBER, TENANT, requestInfo);

        assertEquals(CLONE_NUMBER, service.resolveDataSourceCampaignNumber(CLONE_ID, "user", TENANT, requestInfo));
        verify(service, never()).searchCampaignById(eq(PARENT_ID), any(), any());
    }

    @Test
    void missingParentFallsBackToOwn_generationMustNotFail() {
        doReturn(campaign(CLONE_ID, CLONE_NUMBER, PARENT_ID)).when(service).searchCampaignById(CLONE_ID, TENANT, requestInfo);
        doReturn(Collections.emptyList()).when(service).searchCampaignDataByType("user", null, CLONE_NUMBER, TENANT, requestInfo);
        doThrow(new CustomException("CAMPAIGN_NOT_FOUND", "gone")).when(service).searchCampaignById(PARENT_ID, TENANT, requestInfo);

        assertEquals(CLONE_NUMBER, service.resolveDataSourceCampaignNumber(CLONE_ID, "user", TENANT, requestInfo));
    }

    @Test
    void unresolvableOwnCampaignReturnsNull_likeTheLookupsItReplaced() {
        doThrow(new CustomException("CAMPAIGN_SEARCH_ERROR", "down")).when(service).searchCampaignById(CLONE_ID, TENANT, requestInfo);

        assertNull(service.resolveDataSourceCampaignNumber(CLONE_ID, "user", TENANT, requestInfo));
    }
}
