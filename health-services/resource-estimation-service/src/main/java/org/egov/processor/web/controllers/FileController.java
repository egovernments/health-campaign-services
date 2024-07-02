package org.egov.processor.web.controllers;


import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.egov.common.contract.request.RequestInfo;
import org.egov.processor.service.ExcelParser;
import org.egov.processor.util.ParsingUtil;
import org.egov.processor.util.PlanConfigurationUtil;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.PlanConfigurationSearchCriteria;
import org.egov.processor.web.models.PlanConfigurationSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.stereotype.Controller;


@Controller
public class FileController {

    private ObjectMapper objectMapper;

    private ParsingUtil parsingUtil;

    private ExcelParser parser;

    private PlanConfigurationUtil planConfigurationUtil;

    @Autowired
    public FileController(ObjectMapper objectMapper, ParsingUtil parsingUtil, ExcelParser parser, PlanConfigurationUtil planConfigurationUtil) {
        this.objectMapper = objectMapper;
        this.parsingUtil = parsingUtil;
        this.parser = parser;
        this.planConfigurationUtil = planConfigurationUtil;
    }

    @RequestMapping(value = "/config/_test", method = RequestMethod.POST)
    public ResponseEntity<String> test() {

        PlanConfigurationSearchCriteria planConfigurationSearchCriteria = PlanConfigurationSearchCriteria.builder()
                .tenantId("mz").id("b1a23c4e-a402-4047-9388-e8ae2bf7c1a3").build();

//        id("533db2ad-cfa7-42ce-b9dc-c2877c7405ca")
        PlanConfigurationSearchRequest planConfigurationSearchRequest = PlanConfigurationSearchRequest.builder().planConfigurationSearchCriteria(planConfigurationSearchCriteria).requestInfo(new RequestInfo()).build();
        List<PlanConfiguration> planConfigurationls = planConfigurationUtil.search(planConfigurationSearchRequest);

//        parser.parseFileData(planConfigurationls.get(0));
        return ResponseEntity.status(HttpStatus.OK).body("Okay");
    }

}
