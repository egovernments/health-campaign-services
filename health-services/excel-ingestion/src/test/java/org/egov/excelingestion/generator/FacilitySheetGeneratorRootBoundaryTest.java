package org.egov.excelingestion.generator;

import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.web.models.CampaignSearchResponse.BoundaryDetail;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The root boundary the template's facility search starts from; anything unclear means "search every facility". */
class FacilitySheetGeneratorRootBoundaryTest {

    private static BoundaryDetail boundary(String code, Boolean isRoot) {
        return BoundaryDetail.builder().code(code).isRoot(isRoot).build();
    }

    @Test
    void returnsTheOneBoundaryMarkedRoot() {
        assertEquals("ROOT", FacilitySheetGenerator.campaignRootBoundaryCode(
                List.of(boundary("ROOT", true), boundary("CHILD", false), boundary("OTHER", null))));
    }

    @Test
    void returnsNullWithoutARootBoundary() {
        assertNull(FacilitySheetGenerator.campaignRootBoundaryCode(List.of(boundary("A", false), boundary("B", null))));
    }

    @Test
    void returnsNullWithTwoDifferentRootBoundaries() {
        assertNull(FacilitySheetGenerator.campaignRootBoundaryCode(List.of(boundary("A", true), boundary("B", true))));
    }

    @Test
    void countsARepeatedRootBoundaryOnce() {
        assertEquals("A", FacilitySheetGenerator.campaignRootBoundaryCode(List.of(boundary("A", true), boundary("A", true))));
    }

    @Test
    void returnsNullForNoBoundariesOrABlankRootCode() {
        assertNull(FacilitySheetGenerator.campaignRootBoundaryCode(null));
        assertNull(FacilitySheetGenerator.campaignRootBoundaryCode(List.of(boundary(" ", true))));
    }

    @Test
    void readsTheRootBoundaryFromTheCampaign() {
        CampaignService campaignService = mock(CampaignService.class);
        when(campaignService.getBoundariesFromCampaign(any(), any(), any()))
                .thenReturn(List.of(boundary("ROOT", true), boundary("CHILD", false)));

        assertEquals("ROOT", FacilitySheetGenerator.rootBoundaryCodeOrNull(campaignService, "c-1", "mz", RequestInfo.builder().build()));
    }

    @Test
    void searchesEveryFacilityWhenTheCampaignCannotBeRead() {
        CampaignService campaignService = mock(CampaignService.class);
        when(campaignService.getBoundariesFromCampaign(any(), any(), any())).thenThrow(new RuntimeException("project-factory down"));

        assertNull(FacilitySheetGenerator.rootBoundaryCodeOrNull(campaignService, "c-1", "mz", RequestInfo.builder().build()));
    }
}
