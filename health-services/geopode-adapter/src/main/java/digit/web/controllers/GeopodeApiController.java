package digit.web.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import digit.service.GeopodeAdapterService;
import digit.util.ArcgisUtil;
import digit.util.BoundaryUtil;

import digit.web.models.GeopodeBoundaryRequest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;
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
