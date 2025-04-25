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

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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

        // Fetches hierarchy from GeoPoDe and creates hierarchy in Boundary service.
        geopodeHierarchyCreate(request, typeCodeToFeature);
    }

    /**
     *
     * @param request
     * @param typeCodeToFeature
     */
    private void geopodeHierarchyCreate(GeopodeBoundaryRequest request, Map<String, String> typeCodeToFeature) throws JsonProcessingException {
        //TODO Create request to geopode API to get hierarchy

        String jsonString = RESPONSE_FROM_GEOPODE_API;
        List<GeopdeHierarchyLevel> hierarchyLevels = objectMapper.readValue(jsonString, new TypeReference<List<GeopdeHierarchyLevel>>() {});
        List<BoundaryTypeHierarchy> boundaryHierarchyList = new LinkedList<>();

        hierarchyLevels.forEach(hierarchyLevel -> {
            typeCodeToFeature.put(hierarchyLevel.getTypeCode(), hierarchyLevel.getFeature());

            BoundaryTypeHierarchy boundaryTypeHierarchy = BoundaryTypeHierarchy.builder()
                    .parentBoundaryType(hierarchyLevel.getParent())
                    .boundaryType(hierarchyLevel.getTypeCode())
                    .active(Boolean.TRUE)
                    .build();

            boundaryHierarchyList.add(boundaryTypeHierarchy);
        });

        boundaryUtil.createBoundaryHierarchy(request, boundaryHierarchyList);
    }
}
