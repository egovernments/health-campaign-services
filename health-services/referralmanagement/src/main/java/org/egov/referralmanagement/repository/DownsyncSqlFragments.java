package org.egov.referralmanagement.repository;

import org.egov.tracer.model.CustomException;

/**
 * Shared SQL building blocks for the campaign-wide (root project) PROJECT file queries.
 *
 * <p>Why these exist:
 * <ul>
 *   <li>Field workers register beneficiaries under whichever project they are assigned to
 *       (usually the health-facility level project), while households carry the village
 *       (COMMUNITY) locality code. A per-village PROJECT file must therefore contain the
 *       beneficiaries of <em>every</em> project in the campaign tree who live in that
 *       village — never the beneficiaries of one "leaf" project picked by boundary.</li>
 *   <li>Campaign membership is resolved through {@code project.projecthierarchy}, the
 *       dot-separated path {@code root.….self} maintained by project-service. Nothing is
 *       passed from Java except the root project id.</li>
 *   <li>The beneficiary reference set depends on the campaign's MDMS
 *       {@code beneficiaryType}: HOUSEHOLD campaigns reference household client ids,
 *       INDIVIDUAL campaigns reference individual client ids. Exactly one branch is used.
 *       The former UNION of both doubled the candidate set and forced disk sorts.</li>
 *   <li>Queries that need the village beneficiary set wrap it in
 *       {@code WITH … AS MATERIALIZED} so the planner computes it once. Without that fence
 *       the planner estimates ~1 row for the hierarchy LIKE and re-runs the subquery per
 *       outer row (measured on dev: &gt;15 min vs 1.6 s on an 82k-beneficiary village).</li>
 * </ul>
 * All fragments use the named parameters {@code :rootProjectId} and {@code :locality} and
 * the {@code {schema}} placeholder that the caller resolves per tenant.
 */
public final class DownsyncSqlFragments {

    public static final String BENEFICIARY_TYPE_HOUSEHOLD  = "HOUSEHOLD";
    public static final String BENEFICIARY_TYPE_INDIVIDUAL = "INDIVIDUAL";

    private DownsyncSqlFragments() {}

    /** Sub-select of every project id in the campaign: the root itself plus all descendants. */
    public static final String CAMPAIGN_PROJECT_IDS =
            "SELECT p.id FROM {schema}.PROJECT p " +
            "WHERE COALESCE(p.isDeleted, false) = false " +
            "AND (p.id = :rootProjectId OR p.projecthierarchy LIKE :rootProjectId || '.%')";

    /** Beneficiary client references that live in :locality, for the campaign's beneficiary type. */
    public static String beneficiaryRefsInLocality(String beneficiaryType) {
        String type = normalize(beneficiaryType);
        if (BENEFICIARY_TYPE_HOUSEHOLD.equals(type)) {
            return "SELECT clientReferenceId FROM {schema}.household_address_mv " +
                   "WHERE localitycode = :locality AND isdeleted = false";
        }
        return "SELECT hm.individualClientReferenceId FROM {schema}.HOUSEHOLD_MEMBER hm " +
               "WHERE hm.isDeleted = false AND hm.householdClientReferenceId IN (" +
               "  SELECT clientReferenceId FROM {schema}.household_address_mv " +
               "  WHERE localitycode = :locality AND isdeleted = false)";
    }

    /** Full PROJECT_BENEFICIARY rows of the campaign whose beneficiary lives in :locality. */
    public static String villageBeneficiaries(String beneficiaryType) {
        return "SELECT pb.* FROM {schema}.PROJECT_BENEFICIARY pb " +
               "WHERE pb.isDeleted = false " +
               "AND pb.projectId IN (" + CAMPAIGN_PROJECT_IDS + ") " +
               "AND pb.beneficiaryClientReferenceId IN (" + beneficiaryRefsInLocality(beneficiaryType) + ")";
    }

    /** {@code WITH village_bene AS MATERIALIZED (…)} exposing {@code village_bene.clientReferenceId}. */
    public static String villageBeneficiaryCte(String beneficiaryType) {
        return "WITH village_bene AS MATERIALIZED (" +
               "SELECT pb.clientReferenceId FROM {schema}.PROJECT_BENEFICIARY pb " +
               "WHERE pb.isDeleted = false " +
               "AND pb.projectId IN (" + CAMPAIGN_PROJECT_IDS + ") " +
               "AND pb.beneficiaryClientReferenceId IN (" + beneficiaryRefsInLocality(beneficiaryType) + ")" +
               ") ";
    }

    /** Upper-cases and validates the beneficiary type; throws INVALID_BENEFICIARY_TYPE otherwise. */
    public static String normalize(String beneficiaryType) {
        if (beneficiaryType == null) throw invalid(null);
        String type = beneficiaryType.trim().toUpperCase();
        if (!BENEFICIARY_TYPE_HOUSEHOLD.equals(type) && !BENEFICIARY_TYPE_INDIVIDUAL.equals(type)) {
            throw invalid(beneficiaryType);
        }
        return type;
    }

    private static CustomException invalid(String value) {
        return new CustomException("INVALID_BENEFICIARY_TYPE",
                "Campaign beneficiaryType must be HOUSEHOLD or INDIVIDUAL " +
                "(MDMS HCM-PROJECT-TYPES.projectTypes[].beneficiaryType); got: " + value);
    }
}
