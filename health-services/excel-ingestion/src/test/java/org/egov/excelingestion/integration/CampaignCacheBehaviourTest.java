package org.egov.excelingestion.integration;

import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.repository.GeneratedFileRepository;
import org.egov.excelingestion.repository.ProcessingRepository;
import org.egov.excelingestion.repository.ServiceRequestRepository;
import org.egov.excelingestion.repository.SheetDataTempRepository;
import org.egov.excelingestion.service.CampaignCacheEvictor;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.ConfigBasedProcessingService;
import org.egov.excelingestion.service.FileStoreService;
import org.egov.excelingestion.service.LocalizationService;
import org.egov.excelingestion.web.models.CampaignSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the stale-boundary fix against the real Spring context: an edit after the first read must
 * be visible to the next run once the evictor fires, and all three evictor region names must match.
 */
@SpringBootTest
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})
class CampaignCacheBehaviourTest {

    @Autowired
    private CampaignService campaignService;

    @Autowired
    private CampaignCacheEvictor campaignCacheEvictor;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private ServiceRequestRepository serviceRequestRepository;

    // Context-quieting mocks, same set as ExcelIngestionIntegrationTest.
    @MockBean
    private FileStoreService fileStoreService;

    @MockBean
    private LocalizationService localizationService;

    @MockBean
    private ConfigBasedProcessingService configBasedProcessingService;

    @MockBean
    private GeneratedFileRepository generatedFileRepository;

    @MockBean
    private ProcessingRepository processingRepository;

    @MockBean
    private SheetDataTempRepository sheetDataTempRepository;

    private static final String CAMPAIGN_ID = "campaign-1";
    private static final String TENANT_ID = "dev";

    @BeforeEach
    void clearCampaignCaches() {
        cacheManager.getCache("campaignDetail").clear();
        cacheManager.getCache("campaignBoundaries").clear();
        cacheManager.getCache("campaignProjectType").clear();
    }

    private CampaignSearchResponse response(String projectType, String... codes) {
        List<CampaignSearchResponse.BoundaryDetail> boundaries = java.util.Arrays.stream(codes)
                .map(c -> CampaignSearchResponse.BoundaryDetail.builder().code(c).type("COMMUNITY").build())
                .collect(java.util.stream.Collectors.toList());
        CampaignSearchResponse.CampaignDetail detail = CampaignSearchResponse.CampaignDetail.builder()
                .id(CAMPAIGN_ID)
                .projectType(projectType)
                .boundaries(boundaries)
                .build();
        return CampaignSearchResponse.builder()
                .CampaignDetails(List.of(detail))
                .build();
    }

    private CampaignSearchResponse responseWithBoundaries(String... codes) {
        return response("CO-DELIVERY", codes);
    }

    @Test
    void evictCampaignMakesTheNextReadFresh() {
        // First read caches a 1-boundary campaign; the campaign is then edited to 3 boundaries.
        when(serviceRequestRepository.fetchResult(any(StringBuilder.class), any(), eq(CampaignSearchResponse.class)))
                .thenReturn(responseWithBoundaries("B1"))
                .thenReturn(responseWithBoundaries("B1", "B2", "B3"));

        List<CampaignSearchResponse.BoundaryDetail> run1 =
                campaignService.getBoundariesFromCampaign(CAMPAIGN_ID, TENANT_ID, RequestInfo.builder().build());
        assertEquals(1, run1.size());

        // Without an evict, a re-read inside the TTL serves the pre-edit snapshot (the bug).
        List<CampaignSearchResponse.BoundaryDetail> stillCached =
                campaignService.getBoundariesFromCampaign(CAMPAIGN_ID, TENANT_ID, RequestInfo.builder().build());
        assertEquals(1, stillCached.size());
        verify(serviceRequestRepository, times(1))
                .fetchResult(any(StringBuilder.class), any(), eq(CampaignSearchResponse.class));

        // A new run evicts first and must see the edit.
        campaignCacheEvictor.evictCampaign(CAMPAIGN_ID, TENANT_ID);
        List<CampaignSearchResponse.BoundaryDetail> run2 =
                campaignService.getBoundariesFromCampaign(CAMPAIGN_ID, TENANT_ID, RequestInfo.builder().build());
        assertNotNull(run2);
        assertEquals(3, run2.size());
        verify(serviceRequestRepository, times(2))
                .fetchResult(any(StringBuilder.class), any(), eq(CampaignSearchResponse.class));
    }

    // Guards all three evictor region names: a typo in any one leaves that region stale and fails below.
    @Test
    void evictCoversAllThreeRegions() {
        when(serviceRequestRepository.fetchResult(any(StringBuilder.class), any(), eq(CampaignSearchResponse.class)))
                .thenReturn(response("CO-DELIVERY", "B1"),   // populates campaignDetail
                            response("CO-DELIVERY", "B1"),   // populates campaignBoundaries
                            response("CO-DELIVERY", "B1"),   // populates campaignProjectType
                            response("LLIN", "B1", "B2", "B3"),
                            response("LLIN", "B1", "B2", "B3"),
                            response("LLIN", "B1", "B2", "B3"));

        RequestInfo ri = RequestInfo.builder().build();

        // Populate all three regions.
        assertNotNull(campaignService.searchCampaignById(CAMPAIGN_ID, TENANT_ID, ri));
        assertEquals(1, campaignService.getBoundariesFromCampaign(CAMPAIGN_ID, TENANT_ID, ri).size());
        assertEquals("CO-DELIVERY", campaignService.getProjectTypeFromCampaign(CAMPAIGN_ID, TENANT_ID, ri));
        verify(serviceRequestRepository, times(3))
                .fetchResult(any(StringBuilder.class), any(), eq(CampaignSearchResponse.class));

        // All three now cached: re-reads add no fetches.
        campaignService.searchCampaignById(CAMPAIGN_ID, TENANT_ID, ri);
        campaignService.getBoundariesFromCampaign(CAMPAIGN_ID, TENANT_ID, ri);
        campaignService.getProjectTypeFromCampaign(CAMPAIGN_ID, TENANT_ID, ri);
        verify(serviceRequestRepository, times(3))
                .fetchResult(any(StringBuilder.class), any(), eq(CampaignSearchResponse.class));

        // One evict must clear all three; each post-evict read must see the edited campaign.
        campaignCacheEvictor.evictCampaign(CAMPAIGN_ID, TENANT_ID);
        assertEquals(3, campaignService.searchCampaignById(CAMPAIGN_ID, TENANT_ID, ri).getBoundaries().size());
        assertEquals(3, campaignService.getBoundariesFromCampaign(CAMPAIGN_ID, TENANT_ID, ri).size());
        assertEquals("LLIN", campaignService.getProjectTypeFromCampaign(CAMPAIGN_ID, TENANT_ID, ri));
        verify(serviceRequestRepository, times(6))
                .fetchResult(any(StringBuilder.class), any(), eq(CampaignSearchResponse.class));
    }
}
