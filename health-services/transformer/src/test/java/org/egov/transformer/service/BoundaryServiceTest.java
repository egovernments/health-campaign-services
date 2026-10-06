package org.egov.transformer.service;

import org.egov.transformer.config.TransformerProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BoundaryServiceTest {

    private BoundaryService boundaryServiceWithModuleName(String moduleName) {
        TransformerProperties properties = mock(TransformerProperties.class);
        when(properties.getLocalizationModuleName()).thenReturn(moduleName);
        when(properties.getBoundaryHierarchyName()).thenReturn("ADMIN");
        return new BoundaryService(properties, null, null, null, null);
    }

    @Test
    void localizationModuleAppendsHierarchyToConfiguredPrefix() {
        assertEquals("hcm-boundary-nigeria", boundaryServiceWithModuleName("hcm-boundary-").getLocalizationModule("NIGERIA"));
    }

    @Test
    void localizationModuleIgnoresFullModuleNameConfiguredAsPrefix() {
        assertEquals("hcm-boundary-nigeria", boundaryServiceWithModuleName("hcm-boundary-nigeria").getLocalizationModule("NIGERIA"));
        assertEquals("hcm-boundary-nigeria", boundaryServiceWithModuleName("hcm-boundary-MICROPLAN").getLocalizationModule("NIGERIA"));
    }

    @Test
    void localizationModuleUsesDefaultPrefixWhenNotConfigured() {
        assertEquals("hcm-boundary-sierraleone", boundaryServiceWithModuleName(null).getLocalizationModule("SIERRALEONE"));
    }

    @Test
    void localizationModuleFallsBackToConfiguredHierarchy() {
        assertEquals("hcm-boundary-admin", boundaryServiceWithModuleName("hcm-boundary-").getLocalizationModule(null));
    }

    @Test
    void fallbackNameIsLastSegmentOfCode() {
        assertEquals("CLINIC", BoundaryService.fallbackBoundaryName("NIGERIA_NI_01_01_09_07_BO_CHIBRA_PRIMARY_HEALTH_CLINIC"));
    }

    @Test
    void fallbackNameIgnoresTrailingUnderscores() {
        assertEquals("MAKAMA", BoundaryService.fallbackBoundaryName("SIERRALEONE_SI_01_01_02_09_01_01_MELEKURAY__MAKAMA_"));
    }

    @Test
    void fallbackNameIsCodeWhenNothingElseIsLeft() {
        assertEquals("___", BoundaryService.fallbackBoundaryName("___"));
        assertEquals("KEBBI", BoundaryService.fallbackBoundaryName("KEBBI"));
    }
}
