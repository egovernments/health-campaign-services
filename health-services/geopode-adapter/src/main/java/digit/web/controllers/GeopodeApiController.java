package digit.web.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import digit.service.GeopodeAdapterService;
import digit.web.models.Arcgis.ArcgisRequest;
import digit.web.models.Arcgis.ArcgisResponse;
import digit.web.models.GeopodeBoundaryRequest;
import digit.web.models.boundaryService.BoundaryHierarchyDefinitionResponse;
import digit.web.models.boundaryService.BoundaryHierarchyDefinitionSearchRequest;
import digit.web.models.boundaryService.BoundaryResponse;
import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.model.CustomException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import static digit.config.ServiceConstants.*;

@Slf4j
@Controller
public class GeopodeApiController {

    private GeopodeAdapterService geopodeAdapterService;

    public GeopodeApiController(GeopodeAdapterService geopodeAdapterService) {
        this.geopodeAdapterService = geopodeAdapterService;
    }

    /**
     * Request handler for  create the root and its children's data
     * @param body
     * @return
     */
    @PostMapping("/boundary/setup")
    public ResponseEntity<BoundaryResponse> geopodeBoundaryCreate(@Valid @RequestBody GeopodeBoundaryRequest body) {
        BoundaryResponse boundaryResponse=geopodeAdapterService.createRootBoundaryData(body);
        if (boundaryResponse == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,ROOT_BOUNDARY_ALREADY_EXISTS);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(boundaryResponse);
    }

    /**
     * Request handler for  search for boundary-definition
     * @param request
     * @return
     */
    @PostMapping("/boundary-definition/_search")
    public ResponseEntity<BoundaryHierarchyDefinitionResponse> searchBoundaryDefinition(
            @Valid @RequestBody BoundaryHierarchyDefinitionSearchRequest request) {

        BoundaryHierarchyDefinitionResponse response = null;
        try {
            response = geopodeAdapterService.searchBoundaryHierarchyDefinition(request);
        } catch (CustomException e) {
            log.error(ERROR_IN_SEARCH ,e);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Request handler for  search for arcgis-search
     * @param where
     * @param outFields
     * @param format
     * @param argcgisRequest
     * @return
     */
    @GetMapping("/geopode/arcGis/_search")
    public ResponseEntity<ArcgisResponse> searchRootBoundary(
            @RequestParam String where,
            @RequestParam String outFields,
            @RequestParam(name = "f", defaultValue = "json") String format,
            @Valid @RequestBody ArcgisRequest argcgisRequest) {

        ArcgisRequest request = new ArcgisRequest();
        request.setWhere(where);
        request.setOutFields(outFields);
        request.setF(format);
        request.setRequestInfo(argcgisRequest.getRequestInfo());

        ArcgisResponse response = null;
        try {
            response = geopodeAdapterService.searchBoundary(request);
        } catch (CustomException e) {
            log.error(ERROR_IN_SEARCH, e);
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }



}
