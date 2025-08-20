package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.ProcessResource;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Utility class for enriching GenerateResource objects
 */
@Component
@Slf4j
public class EnrichmentUtil {

    /**
     * Enriches GenerateResource with UUID v4 ID and sets status to in_progress
     *
     * @param generateResource The resource to enrich
     */
    public void enrichGenerateResource(GenerateResource generateResource) {
        // Generate and set UUID v4 for the resource ID
        String resourceId = UUID.randomUUID().toString();
        generateResource.setId(resourceId);
        
        // Set status to in_progress when processing starts
        generateResource.setStatus(ProcessingConstants.STATUS_IN_PROGRESS);
        log.info("Excel generation started for type: {}, ID: {}, status set to in_progress", 
                generateResource.getType(), resourceId);
    }

    /**
     * Enriches ProcessResource with UUID v4 ID and sets status to in_progress
     *
     * @param processResource The resource to enrich
     */
    public void enrichProcessResource(ProcessResource processResource) {
        // Generate and set UUID v4 for the resource ID
        String resourceId = UUID.randomUUID().toString();
        processResource.setId(resourceId);
        
        // Set status to in_progress when processing starts
        processResource.setStatus(ProcessingConstants.STATUS_IN_PROGRESS);
        log.info("Data processing started for type: {}, ID: {}, status set to in_progress", 
                processResource.getType(), resourceId);
    }
}