package org.egov.errorhandler.controller;

import org.egov.errorhandler.models.DumpRequest;
import org.egov.tracer.ExceptionAdvise;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.validation.Valid;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
@RequestMapping("")
public class ErrorHandlerController {

    @Autowired
    private ExceptionAdvise exceptionAdvise;

    @RequestMapping(value = "/handle-error", method = RequestMethod.POST)
    public ResponseEntity<String> handlerErrors(@Valid @RequestBody DumpRequest request) {
        if (ObjectUtils.isEmpty(request.getErrorDetail().getApiDetails().getId())) {
            request.getErrorDetail().getApiDetails().setId(UUID.randomUUID().toString());
        }

        exceptionAdvise.exceptionHandler(Stream.of(request.getErrorDetail()).collect(Collectors.toList()));

        return ResponseEntity.status(HttpStatus.OK).body("{\"Success\":\"True\"}");
    }

}
