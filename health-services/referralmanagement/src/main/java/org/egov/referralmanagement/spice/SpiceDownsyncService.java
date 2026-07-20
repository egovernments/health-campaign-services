package org.egov.referralmanagement.spice;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.referralmanagement.beneficiarydownsync.Downsync;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncCriteria;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncRequest;
import org.egov.referralmanagement.spice.model.SpiceHousehold;
import org.egov.referralmanagement.spice.model.SpiceMember;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@link Downsync} live from Spice instead of the local HCM tables.
 *
 * <p>Only Households / Individuals / HouseholdMembers are populated — the models Spice has no source
 * for (ProjectBeneficiaries, Tasks, SideEffects, Referrals, HFReferrals, Services) are left null.
 * Nothing is persisted. Independent of {@code DownsyncService}; no shared code path.</p>
 */
@Slf4j
@Service
public class SpiceDownsyncService {

    /** Default village when the request carries no locality (per requirement). */
    private static final long DEFAULT_VILLAGE_ID = 64L;
    private static final String DEFAULT_LOCALITY = "SL_C1_D16_CH24_V64";

    private final SpiceApiClient spiceApiClient;
    private final SpiceMapper mapper;

    @Autowired
    public SpiceDownsyncService(SpiceApiClient spiceApiClient, SpiceMapper mapper) {
        this.spiceApiClient = spiceApiClient;
        this.mapper = mapper;
    }

    public Downsync prepareDownsyncData(DownsyncRequest request) {
        DownsyncCriteria criteria = request.getDownsyncCriteria();
        String tenantId = criteria.getTenantId();

        String locality = (criteria.getLocality() == null || criteria.getLocality().isBlank())
                ? DEFAULT_LOCALITY : criteria.getLocality();
        long villageId = parseVillageId(locality);
        String wardCode = deriveWardCode(locality);

        log.info("Spice downsync — locality={} villageId={} tenant={} lastSyncedTime={}",
                locality, villageId, tenantId, criteria.getLastSyncedTime());

        // live pulls from Spice
        List<SpiceHousehold> spiceHouseholds = spiceApiClient.getHouseholds(villageId, criteria.getLastSyncedTime());
        List<SpiceMember> spiceMembers = spiceApiClient.getMembers(villageId, criteria.getLastSyncedTime());

        List<Household> households = new ArrayList<>();
        for (SpiceHousehold sh : spiceHouseholds)
            households.add(mapper.toHousehold(sh, locality, tenantId));

        List<Individual> individuals = new ArrayList<>();
        List<HouseholdMember> members = new ArrayList<>();
        int skippedNoHousehold = 0;
        for (SpiceMember sm : spiceMembers) {
            // the person always maps to an Individual
            individuals.add(mapper.toIndividual(sm, locality, wardCode, tenantId));
            // a HouseholdMember can only be formed when the person is attached to a household in Spice
            if (sm.getHouseholdId() != null && !sm.getHouseholdId().isBlank()) {
                members.add(mapper.toHouseholdMember(sm, tenantId));
            } else {
                skippedNoHousehold++;
            }
        }
        if (skippedNoHousehold > 0)
            log.info("Skipped {} HouseholdMember link(s) — members with no household in Spice (Individuals still returned)",
                    skippedNoHousehold);

        criteria.setTotalCount((long) households.size());

        Downsync downsync = new Downsync();
        downsync.setDownsyncCriteria(criteria);
        downsync.setHouseholds(households);
        downsync.setIndividuals(individuals);
        downsync.setHouseholdMembers(members);
        // Not available in Spice -> left null intentionally:
        // ProjectBeneficiaries, Tasks, SideEffects, Referrals, HFReferrals, Services
        return downsync;
    }

    /** Village id is encoded in the SPICE_SL locality code, e.g. {@code SL_C1_D16_CH24_V64 -> 64}. */
    private long parseVillageId(String locality) {
        int i = locality.lastIndexOf("_V");
        if (i >= 0) {
            try {
                return Long.parseLong(locality.substring(i + 2));
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return DEFAULT_VILLAGE_ID;
    }

    /** Ward = chiefdom-level code = locality with the trailing {@code _V<id>} stripped. */
    private String deriveWardCode(String locality) {
        int i = locality.lastIndexOf("_V");
        return i >= 0 ? locality.substring(0, i) : null;
    }
}
