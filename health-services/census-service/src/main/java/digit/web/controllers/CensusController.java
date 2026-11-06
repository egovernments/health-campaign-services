package digit.web.controllers;

import digit.service.CensusService;
import digit.web.models.BulkCensusRequest;
import digit.web.models.CensusRequest;
import digit.web.models.CensusResponse;
import digit.web.models.CensusSearchRequest;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;


@Controller
public class CensusController {

    private CensusService censusService;

    public CensusController(CensusService censusService) {
        this.censusService = censusService;
    }

    /**
     * Request handler for serving census create requests
     *
     * @param body
     * @return
     */
    @RequestMapping(value = "/_create", method = RequestMethod.POST)
    public ResponseEntity<CensusResponse> create(@Parameter(in = ParameterIn.DEFAULT, description = "", schema = @Schema()) @Valid @RequestBody CensusRequest body) {
        CensusResponse response = censusService.create(body);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Request handler for serving census search requests
     *
     * @param body
     * @return
     */
    @RequestMapping(value = "/_search", method = RequestMethod.POST)
    public ResponseEntity<CensusResponse> search(@Parameter(in = ParameterIn.DEFAULT, description = "", schema = @Schema()) @Valid @RequestBody CensusSearchRequest body) {
        CensusResponse response = censusService.search(body);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Request handler for serving census update requests
     *
     * @param body
     * @return
     */
    @RequestMapping(value = "/_update", method = RequestMethod.POST)
    public ResponseEntity<CensusResponse> update(@Parameter(in = ParameterIn.DEFAULT, description = "", schema = @Schema()) @Valid @RequestBody CensusRequest body) {
        CensusResponse response = censusService.update(body);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Request handler for serving bulk census update requests
     *
     * @param body
     * @return
     */
    @RequestMapping(value = "/bulk/_update", method = RequestMethod.POST)
    public ResponseEntity<CensusResponse> bulkUpdate(@Parameter(in = ParameterIn.DEFAULT, description = "", schema = @Schema()) @Valid @RequestBody BulkCensusRequest body) {
        CensusResponse response = censusService.bulkUpdate(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
