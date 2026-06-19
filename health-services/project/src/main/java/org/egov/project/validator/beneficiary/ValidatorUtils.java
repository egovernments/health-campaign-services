package org.egov.project.validator.beneficiary;

import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectBeneficiary;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

public class ValidatorUtils {
    /**
     * This method validates the uniqueness of voucher tags among valid ProjectBeneficiary entities.
     *
     * @param validProjectBeneficiaries List of valid ProjectBeneficiary entities.
     * @param errorDetailsMap           A map to store error details for duplicate voucher tags.
     */
    public static void validateUniqueTags(List<ProjectBeneficiary> validProjectBeneficiaries, Map<ProjectBeneficiary, List<Error>> errorDetailsMap) {
        // Group ProjectBeneficiaries by voucher tags
        Map<String, List<ProjectBeneficiary>> map = validProjectBeneficiaries.stream().filter(projectBeneficiary -> projectBeneficiary.getTag() != null)
                .collect(Collectors.groupingBy(ProjectBeneficiary::getTag));

        // Find voucher tags with duplicates
        List<ProjectBeneficiary> duplicates = map.values().stream()
                .filter(projectBeneficiaries -> projectBeneficiaries.size() > 1)
                .flatMap(List::stream)
                .filter(notHavingErrors())
                .collect(Collectors.toList());

        // Populate error details for entities with duplicate voucher tags
        for (ProjectBeneficiary projectBeneficiary : duplicates) {
            Error error = getErrorForUniqueEntity();
            populateErrorDetails(projectBeneficiary, error, errorDetailsMap);
        }
    }
}
