package org.egov.excelingestion.generator;

import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.CryptoService;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.util.ExcelDataPopulator;
import org.egov.excelingestion.util.HierarchicalBoundaryUtil;
import org.egov.excelingestion.util.SchemaColumnDefUtil;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * A clone's template is the only place its inherited users can be protected, so a pre-fill failure on
 * that path must fail the generation. On a campaign's own path the historical headers-only fallback stays.
 */
@ExtendWith(MockitoExtension.class)
class UserSheetGeneratorClonePrefillTest {

    @Mock private MDMSService mdmsService;
    @Mock private CampaignService campaignService;
    @Mock private BoundaryService boundaryService;
    @Mock private BoundaryUtil boundaryUtil;
    @Mock private CustomExceptionHandler exceptionHandler;
    @Mock private SchemaColumnDefUtil schemaColumnDefUtil;
    @Mock private ExcelDataPopulator excelDataPopulator;
    @Mock private HierarchicalBoundaryUtil hierarchicalBoundaryUtil;
    @Mock private CryptoService cryptoService;

    private UserSheetGenerator generator;
    private GenerateResource resource;
    private final RequestInfo requestInfo = new RequestInfo();

    @BeforeEach
    void setUp() {
        generator = new UserSheetGenerator(mdmsService, campaignService, boundaryService, boundaryUtil,
                exceptionHandler, schemaColumnDefUtil, excelDataPopulator, hierarchicalBoundaryUtil, cryptoService);
        resource = new GenerateResource();
        resource.setId("gen-1");
        resource.setReferenceId("clone-uuid");
        resource.setTenantId("dev");
        resource.setHierarchyType("ADMIN");
    }

    @Test
    void clonePathFailsClosedWhenTheSourceUsersCannotBeRead() {
        when(campaignService.resolveDataSource("clone-uuid", "user", "dev", requestInfo))
                .thenReturn(new CampaignService.DataSource("CMP-PARENT", true));
        when(campaignService.searchCampaignDataByUniqueIdentifiers(anyList(), eq("user"), any(), eq("CMP-PARENT"), eq("dev"), any()))
                .thenThrow(new CustomException("CAMPAIGN_DATA_SEARCH_ERROR", "project-factory down"));

        assertThrows(RuntimeException.class, () -> generator.fetchExistingUserData(resource, requestInfo));
    }

    @Test
    void ownPathKeepsTheHeadersOnlyFallback() {
        when(campaignService.resolveDataSource("clone-uuid", "user", "dev", requestInfo))
                .thenReturn(new CampaignService.DataSource("CMP-OWN", false));
        when(campaignService.searchCampaignDataByUniqueIdentifiers(anyList(), eq("user"), any(), eq("CMP-OWN"), eq("dev"), any()))
                .thenThrow(new CustomException("CAMPAIGN_DATA_SEARCH_ERROR", "project-factory down"));

        assertNull(generator.fetchExistingUserData(resource, requestInfo));
    }
}
