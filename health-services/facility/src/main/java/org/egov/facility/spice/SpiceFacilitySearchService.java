package org.egov.facility.spice;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilitySearchRequest;
import org.egov.facility.spice.model.SpiceFacility;
import org.egov.facility.spice.model.SpiceFacilityPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Live facility search against Spice, keyed off the same SPICE_SL locality code used for the
 * household downsync. Spice can't filter facilities by village, so we resolve the village's
 * CHIEFDOM (+DISTRICT) from the code, list that chiefdom's facilities, then keep only the ones
 * whose linkedVillages include the village (village-exact). Nothing is persisted.
 */
@Slf4j
@Service
public class SpiceFacilitySearchService {

    private static final long DEFAULT_VILLAGE_ID = 64L;
    private static final int DEFAULT_DISTRICT_ID = 16;
    private static final int DEFAULT_CHIEFDOM_ID = 24;
    /** upper bound for the chiefdom fetch (chiefdoms hold few facilities). */
    private static final int CHIEFDOM_FETCH_LIMIT = 1000;

    private final SpiceFacilityClient client;
    private final SpiceFacilityMapper mapper;

    @Autowired
    public SpiceFacilitySearchService(SpiceFacilityClient client, SpiceFacilityMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    public Result search(FacilitySearchRequest request, String tenantId, Integer limit, Integer offset) {
        String locality = request.getFacility() != null ? request.getFacility().getLocalityCode() : null;

        int districtId = DEFAULT_DISTRICT_ID, chiefdomId = DEFAULT_CHIEFDOM_ID;
        Long villageId = DEFAULT_VILLAGE_ID;
        if (locality != null && !locality.isBlank()) {
            districtId = parseSegment(locality, "_D", DEFAULT_DISTRICT_ID);
            chiefdomId = parseSegment(locality, "_CH", DEFAULT_CHIEFDOM_ID);
            villageId = (long) parseSegment(locality, "_V", -1);
        }

        log.info("Spice facility search — locality={} districtId={} chiefdomId={} villageId={} tenant={}",
                locality, districtId, chiefdomId, villageId, tenantId);

        // fetch all facilities in the chiefdom, then filter to those serving the village
        SpiceFacilityPage page = client.listFacilities(List.of(chiefdomId), List.of(districtId), 0, CHIEFDOM_FETCH_LIMIT);

        List<SpiceFacility> matched = new ArrayList<>();
        for (SpiceFacility sf : page.getFacilities()) {
            if (villageId < 0 || linksVillage(sf, villageId)) matched.add(sf);
        }

        // apply the caller's paging in-memory over the village-exact set
        int off = offset == null ? 0 : Math.max(0, offset);
        int lim = (limit == null || limit <= 0) ? matched.size() : limit;
        int from = Math.min(off, matched.size());
        int to = Math.min(from + lim, matched.size());

        List<Facility> facilities = new ArrayList<>();
        for (SpiceFacility sf : matched.subList(from, to))
            facilities.add(mapper.toFacility(sf, tenantId));

        return new Result(facilities, (long) matched.size());
    }

    private boolean linksVillage(SpiceFacility sf, long villageId) {
        if (sf.getLinkedVillages() == null) return false;
        return sf.getLinkedVillages().stream()
                .anyMatch(v -> v.getId() != null && v.getId().longValue() == villageId);
    }

    private int parseSegment(String code, String marker, int fallback) {
        int i = code.indexOf(marker);
        if (i < 0) return fallback;
        int start = i + marker.length();
        int end = start;
        while (end < code.length() && Character.isDigit(code.charAt(end))) end++;
        if (end == start) return fallback;
        return Integer.parseInt(code.substring(start, end));
    }

    /** Facilities + village-exact total count. */
    public record Result(List<Facility> facilities, Long totalCount) {}
}
