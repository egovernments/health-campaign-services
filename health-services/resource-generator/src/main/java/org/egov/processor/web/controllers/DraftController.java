package org.egov.processor.web.controllers;

import jakarta.validation.Valid;
import org.egov.processor.service.DraftService;
import org.egov.processor.web.models.DraftRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
public class DraftController {

    private DraftService draftService;

    public DraftController(DraftService draftService) {
        this.draftService = draftService;
    }

    @PostMapping("/draft")
    public ResponseEntity<String> createDraftMicroplan(@RequestBody @Valid DraftRequest draftRequest) {
        draftService.createDraftPlans(draftRequest);
        return ResponseEntity.accepted().body("Draft processing started successfully.");
    }
}
