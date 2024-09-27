package digit.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.service.CensusService;
import digit.util.ResponseInfoFactory;
import digit.web.models.CensusRequest;
import digit.web.models.CensusResponse;
import digit.web.models.CensusSearchRequest;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;

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
    public ResponseEntity<CensusResponse> censusCreatePost(@Parameter(in = ParameterIn.DEFAULT, description = "", schema = @Schema()) @Valid @RequestBody CensusRequest body) {

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
    public ResponseEntity<CensusResponse> censusSearchPost(@Parameter(in = ParameterIn.DEFAULT, description = "", schema = @Schema()) @Valid @RequestBody CensusSearchRequest body) {

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
    public ResponseEntity<CensusResponse> censusUpdatePost(@Parameter(in = ParameterIn.DEFAULT, description = "", schema = @Schema()) @Valid @RequestBody CensusRequest body) {

        CensusResponse response = censusService.update(body);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
