package org.egov.individual.validator;

import org.egov.common.models.Error;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkRequest;
import org.egov.individual.validators.IRequiredLinkValidator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IRequiredLinkValidatorTest {

    private final IRequiredLinkValidator validator = new IRequiredLinkValidator();

    @Test
    void shouldRejectOnlyIndividualWithBlankClientReferenceId() {
        Individual invalid = Individual.builder().clientReferenceId("  ").hasErrors(false).build();
        Individual valid = Individual.builder().clientReferenceId("device-individual-1")
                .hasErrors(false).build();
        IndividualBulkRequest request = IndividualBulkRequest.builder()
                .individuals(Arrays.asList(invalid, valid)).build();

        Map<Individual, List<Error>> errors = validator.validate(request);

        assertEquals(1, errors.size());
        assertTrue(errors.containsKey(invalid));
        assertFalse(errors.containsKey(valid));
        assertEquals("REQUIRED_LINK_MISSING", errors.get(invalid).get(0).getErrorCode());
    }

    @Test
    void shouldAcceptIndividualWithClientReferenceId() {
        Individual valid = Individual.builder().clientReferenceId("device-individual-1").build();

        assertTrue(validator.validate(IndividualBulkRequest.builder()
                .individuals(List.of(valid)).build()).isEmpty());
    }
}
