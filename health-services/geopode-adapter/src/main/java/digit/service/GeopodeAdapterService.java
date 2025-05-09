package digit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.util.BoundaryUtil;
import digit.web.models.GeopdeHierarchyLevel;
import digit.web.models.GeopodeBoundaryRequest;
import digit.web.models.boundaryService.BoundaryTypeHierarchy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static digit.config.ServiceConstants.RESPONSE_FROM_GEOPODE_API;

@Service
public class GeopodeAdapterService {
    private ObjectMapper objectMapper;
    private BoundaryUtil boundaryUtil;

    public GeopodeAdapterService(ObjectMapper objectMapper, BoundaryUtil boundaryUtil) {
        this.objectMapper = objectMapper;
        this.boundaryUtil = boundaryUtil;
    }

    /**
     *
     * @param request
     * @throws JsonProcessingException
     */
    @Async
    public void createBoundaryData(GeopodeBoundaryRequest request) throws JsonProcessingException {

        Map<String, String> typeCodeToFeature = new LinkedHashMap<>();
        List<GeopdeHierarchyLevel> hierarchyLevels = new ArrayList<>();

        // Fetches hierarchy from GeoPoDe api and creates hierarchy in Boundary service.
        geopodeHierarchyCreate(request, typeCodeToFeature, hierarchyLevels);

        // Fetches boundaries from GeoPoDe api and creates boundary tree in Boundary service.
        geopodeBoundaryCreate(request, typeCodeToFeature, hierarchyLevels);
    }

    /**
     *
     * @param typeCodeToFeature
     * @param hierarchyLevels
     */
    private void geopodeBoundaryCreate(GeopodeBoundaryRequest request, Map<String, String> typeCodeToFeature, List<GeopdeHierarchyLevel> hierarchyLevels) {
        Map<String, String> parentToChildMap = createParentToChildMap(hierarchyLevels);

        String rootHierarchy = hierarchyLevels.stream()
                .filter(level -> level.getParent() == null)
                .findFirst()
                .map(GeopdeHierarchyLevel::getTypeCode)
                .orElse(null);

    }

    /**
     * Fetches and processes boundary hierarchy data from the Geopode API response,
     * enriches the typeCode-to-feature mapping, and prepares the boundary hierarchy
     * structure to be created in the boundary service.
     *
     * @param request the original request containing metadata required for boundary hierarchy creation.
     * @param typeCodeToFeature a map to be populated with typeCode as key and corresponding feature as value.
     * @param hierarchyLevels an initially empty list that will be populated with parsed hierarchy levels.
     * @throws JsonProcessingException if the Geopode API response JSON cannot be parsed.
     */
    private void geopodeHierarchyCreate(GeopodeBoundaryRequest request, Map<String, String> typeCodeToFeature, List<GeopdeHierarchyLevel> hierarchyLevels) throws JsonProcessingException {
        //TODO : Create request to geopode API to get hierarchy

        String jsonString = RESPONSE_FROM_GEOPODE_API;
        hierarchyLevels = objectMapper.readValue(jsonString, new TypeReference<List<GeopdeHierarchyLevel>>() {});
        List<BoundaryTypeHierarchy> boundaryHierarchyList = new LinkedList<>();

        hierarchyLevels.forEach(hierarchyLevel -> {
            // Enrich typeCodeToFeature map
            typeCodeToFeature.put(hierarchyLevel.getTypeCode(), hierarchyLevel.getFeature());

            BoundaryTypeHierarchy boundaryTypeHierarchy = BoundaryTypeHierarchy.builder()
                    .parentBoundaryType(hierarchyLevel.getParent())
                    .boundaryType(hierarchyLevel.getTypeCode())
                    .active(Boolean.TRUE)
                    .build();

            boundaryHierarchyList.add(boundaryTypeHierarchy);
        });

        // Call boundary service hierarchy create api to create hierarchy in boundary service.
        boundaryUtil.createBoundaryHierarchy(request, boundaryHierarchyList);
    }

    /**
     * Creates a mapping of parent hierarchy type codes to their corresponding child type codes
     * from a list of GeopdeHierarchyLevel objects.
     *
     * @param hierarchyLevels the list of hierarchy levels to process.
     * @return a map where each key is a parent type code and the value is its corresponding child type code.
     */
    private Map<String, String> createParentToChildMap(List<GeopdeHierarchyLevel> hierarchyLevels) {
        return hierarchyLevels.stream()
                .filter(h -> h.getParent() != null)
                .collect(Collectors.toMap(
                        GeopdeHierarchyLevel::getParent,
                        GeopdeHierarchyLevel::getTypeCode));
    }
}
