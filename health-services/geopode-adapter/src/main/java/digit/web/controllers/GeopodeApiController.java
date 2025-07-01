package digit.web.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import digit.service.GeopodeAdapterService;
import digit.util.ArcgisUtil;
import digit.util.BoundaryUtil;
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
    private BoundaryUtil boundaryUtil;
    private ArcgisUtil arcgisUtil;

    public GeopodeApiController(GeopodeAdapterService geopodeAdapterService) {
        this.geopodeAdapterService = geopodeAdapterService;
    }

    /**
     * Request handler for  create the root and its children's data
     *
     * @param body
     * @return
     */
    @PostMapping("/boundary/setup")
    public ResponseEntity<String> geopodeBoundaryCreate(@Valid @RequestBody GeopodeBoundaryRequest body) {
        String countryName= geopodeAdapterService.createRootBoundaryData(body);
        if (countryName == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ROOT_BOUNDARY_ALREADY_EXISTS);
        }
        String message = BOUNDARY_CREATION_INITIATED + countryName + ".";
        return ResponseEntity.status(HttpStatus.OK).body(message);
    }

}
