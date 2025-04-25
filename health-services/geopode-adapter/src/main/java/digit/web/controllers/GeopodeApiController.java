package digit.web.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import digit.service.GeopodeAdapterService;
import digit.web.models.GeopodeBoundaryRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;
import static digit.config.ServiceConstants.BOUNDARY_CREATION_RESPONSE;

@Controller
public class GeopodeApiController {

    private GeopodeAdapterService geopodeAdapterService;

    public GeopodeApiController(GeopodeAdapterService geopodeAdapterService) {
        this.geopodeAdapterService = geopodeAdapterService;
    }

    @PostMapping("/geopode/boundary/_create")
    public ResponseEntity<String> geopodeBoundaryCreate(@Valid @RequestBody GeopodeBoundaryRequest body) throws JsonProcessingException {
        geopodeAdapterService.createBoundaryData(body);
        return ResponseEntity.accepted().body(BOUNDARY_CREATION_RESPONSE);
    }

}
