package org.egov.excelingestion.generator;

import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.FacilityService;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.util.CellProtectionManager;
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
import static org.mockito.Mockito.when;

/** Facility twin of UserSheetGeneratorClonePrefillTest: the clone path fails closed, the own path keeps its fallback. */
@ExtendWith(MockitoExtension.class)
class FacilitySheetGeneratorClonePrefillTest {

    @Mock private MDMSService mdmsService;
    @Mock private CampaignService campaignService;
    @Mock private FacilityService facilityService;
    @Mock private BoundaryService boundaryService;
    @Mock private BoundaryUtil boundaryUtil;
    @Mock private CellProtectionManager cellProtectionManager;
    @Mock private CustomExceptionHandler exceptionHandler;
    @Mock private SchemaColumnDefUtil schemaColumnDefUtil;
    @Mock private ExcelDataPopulator excelDataPopulator;
    @Mock private HierarchicalBoundaryUtil hierarchicalBoundaryUtil;

    private FacilitySheetGenerator generator;
    private GenerateResource resource;
    private final RequestInfo requestInfo = new RequestInfo();

    @BeforeEach
    void setUp() {
        generator = new FacilitySheetGenerator(mdmsService, campaignService, facilityService, boundaryService,
                boundaryUtil, cellProtectionManager, exceptionHandler, schemaColumnDefUtil, excelDataPopulator,
                hierarchicalBoundaryUtil);
        resource = new GenerateResource();
        resource.setId("gen-1");
        resource.setReferenceId("clone-uuid");
        resource.setTenantId("dev");
        resource.setHierarchyType("ADMIN");
    }

    @Test
    void clonePathFailsClosedWhenTheSourceCannotBeRead() {
        when(campaignService.resolveDataSource("clone-uuid", "facility", "dev", requestInfo))
                .thenReturn(new CampaignService.DataSource("CMP-PARENT", true));
        when(facilityService.fetchAllPermanentFacilities("dev", requestInfo))
                .thenThrow(new CustomException("FACILITY_SEARCH_ERROR", "facility service down"));

        assertThrows(RuntimeException.class, () -> generator.fetchExistingFacilityData(resource, requestInfo));
    }

    @Test
    void ownPathKeepsTheHeadersOnlyFallback() {
        when(campaignService.resolveDataSource("clone-uuid", "facility", "dev", requestInfo))
                .thenReturn(new CampaignService.DataSource("CMP-OWN", false));
        when(facilityService.fetchAllPermanentFacilities("dev", requestInfo))
                .thenThrow(new CustomException("FACILITY_SEARCH_ERROR", "facility service down"));

        assertNull(generator.fetchExistingFacilityData(resource, requestInfo));
    }
}
