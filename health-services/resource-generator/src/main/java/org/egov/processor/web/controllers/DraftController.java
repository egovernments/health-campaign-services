package org.egov.processor.web.controllers;

import jakarta.validation.Valid;
import org.egov.processor.service.DraftService;
import org.egov.processor.web.models.DraftRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import static org.egov.processor.config.ServiceConstants.DRAFT_RESPONSE;

@Controller
public class DraftController {

    private DraftService draftService;

    public DraftController(DraftService draftService) {
        this.draftService = draftService;
    }

    @PostMapping("/drafts")
    public ResponseEntity<String> createDraftMicroplan(@RequestBody @Valid DraftRequest draftRequest) {
        draftService.createDraftPlans(draftRequest);
        return ResponseEntity.accepted().body(DRAFT_RESPONSE);
    }
}
