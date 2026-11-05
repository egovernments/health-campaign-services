package org.egov.facility.validator;

import org.egov.common.models.Error;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilityBulkRequest;
import org.egov.common.models.core.Field;
import org.egov.facility.config.FacilityConfiguration;
import org.egov.facility.service.OutsideHouseholdTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Bug 3: a site made through the API is checked like the Excel upload (name length, category of its type). */
@ExtendWith(MockitoExtension.class)
class FOutsideHouseholdSiteValidatorTest {

    @Mock
    private OutsideHouseholdTypeService outsideHouseholdTypeService;
    @Mock
    private FacilityConfiguration configuration;

    private FOutsideHouseholdSiteValidator validator;

    @BeforeEach
    void setUp() {
        lenient().when(configuration.getValidateOutsideHouseholdSites()).thenReturn(true);
        lenient().when(configuration.getOutsideHouseholdSiteNameMinLength()).thenReturn(2);
        lenient().when(configuration.getOutsideHouseholdSiteNameMaxLength()).thenReturn(200);
        lenient().when(outsideHouseholdTypeService.getCategoriesByType(any())).thenReturn(Map.of(
                "OUTSIDE_HOUSEHOLD_PLACES", List.of("SCHOOL", "MARKET"), "TRANSIT_POST", List.of("BUS_STATION", "CHECKPOINT")));
        validator = new FOutsideHouseholdSiteValidator(outsideHouseholdTypeService, configuration);
    }

    private static Facility site(String name, String usage, String category) {
        AdditionalFields fields = category == null ? null : AdditionalFields.builder()
                .fields(List.of(Field.builder().key("outsideHouseholdCategory").value(category).build())).build();
        return Facility.builder().tenantId("dev").name(name).usage(usage).additionalFields(fields).build();
    }

    private Map<String, List<String>> errorCodesByName(Facility... facilities) {
        Map<Facility, List<Error>> errors = validator.validate(FacilityBulkRequest.builder().facilities(List.of(facilities)).build());
        return errors.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().getName(),
                e -> e.getValue().stream().map(Error::getErrorCode).sorted().toList()));
    }

    @Test
    void rejectsTheSitesTheUploadWouldRejectAndAcceptsTheRest() {
        Map<String, List<String>> errors = errorCodesByName(
                site("Zabi School", "OUTSIDE_HOUSEHOLD_PLACES", "SCHOOL"),
                site("N".repeat(302), "OUTSIDE_HOUSEHOLD_PLACES", "SCHOOL"),
                site("N", "TRANSIT_POST", "CHECKPOINT"),
                site("Bad Category", "OUTSIDE_HOUSEHOLD_PLACES", "NOT_A_CATEGORY"),
                site("Transit With School", "TRANSIT_POST", "school"),
                site("No Category Yet", "TRANSIT_POST", null),
                site("M".repeat(200), "outside_household_places", "market"),
                site("X", "Warehouse", null));

        assertEquals(Map.of(
                "N".repeat(302), List.of(FOutsideHouseholdSiteValidator.INVALID_NAME),
                "N", List.of(FOutsideHouseholdSiteValidator.INVALID_NAME),
                "Bad Category", List.of(FOutsideHouseholdSiteValidator.INVALID_CATEGORY),
                "Transit With School", List.of(FOutsideHouseholdSiteValidator.INVALID_CATEGORY)), errors);
    }

    @Test
    void aSiteNameMayNotUseTheBlockedCharacters() {
        Map<String, List<String>> errors = errorCodesByName(
                site("St. Mary's (Sede) 1/2 - N'Gola", "OUTSIDE_HOUSEHOLD_PLACES", "SCHOOL"),
                site("Market & Shop_1, `A` (B)", "OUTSIDE_HOUSEHOLD_PLACES", "MARKET"),
                site("Market {B}", "OUTSIDE_HOUSEHOLD_PLACES", "MARKET"),
                site("Post \"C\"", "TRANSIT_POST", "CHECKPOINT"),
                site("Clinic; South", "TRANSIT_POST", "CHECKPOINT"),
                site("Post <b>", "TRANSIT_POST", "CHECKPOINT"),
                site("Back\\slash", "TRANSIT_POST", "CHECKPOINT"),
                site("Line\nBreak", "TRANSIT_POST", "CHECKPOINT"),
                site("+North", "TRANSIT_POST", "CHECKPOINT"),
                site("North 1+1", "TRANSIT_POST", "CHECKPOINT"),
                site("North 1+1=2", "TRANSIT_POST", "CHECKPOINT"),
                site("Post \u201CC\u201D", "TRANSIT_POST", "CHECKPOINT"),
                site("St. Mary\u2019s Post", "TRANSIT_POST", "CHECKPOINT"),
                site("Store #1", "Warehouse", null));   // not a site: not checked

        assertEquals(Map.of(
                "Clinic; South", List.of(FOutsideHouseholdSiteValidator.INVALID_NAME),
                "Post <b>", List.of(FOutsideHouseholdSiteValidator.INVALID_NAME),
                "Back\\slash", List.of(FOutsideHouseholdSiteValidator.INVALID_NAME),
                "Line\nBreak", List.of(FOutsideHouseholdSiteValidator.INVALID_NAME),
                "+North", List.of(FOutsideHouseholdSiteValidator.INVALID_NAME),
                "Market {B}", List.of(FOutsideHouseholdSiteValidator.INVALID_NAME),
                "Post \"C\"", List.of(FOutsideHouseholdSiteValidator.INVALID_NAME),
                "North 1+1", List.of(FOutsideHouseholdSiteValidator.INVALID_NAME),
                "North 1+1=2", List.of(FOutsideHouseholdSiteValidator.INVALID_NAME),
                "Post \u201CC\u201D", List.of(FOutsideHouseholdSiteValidator.INVALID_NAME)), errors);
    }

    @Test
    void checksNothingWhenSwitchedOff() {
        when(configuration.getValidateOutsideHouseholdSites()).thenReturn(false);

        assertTrue(errorCodesByName(site("N".repeat(302), "OUTSIDE_HOUSEHOLD_PLACES", "NOT_A_CATEGORY")).isEmpty());
    }

    @Test
    void checksNothingWhenTheSiteTypesCannotBeRead() {
        when(outsideHouseholdTypeService.getCategoriesByType(any())).thenReturn(Map.of());

        assertTrue(errorCodesByName(site("N".repeat(302), "OUTSIDE_HOUSEHOLD_PLACES", "NOT_A_CATEGORY")).isEmpty());
    }
}
