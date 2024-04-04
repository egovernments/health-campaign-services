package org.egov.processor.service;


import org.egov.processor.web.models.PlanConfiguration;

public interface FileParser {

    public default void parseFileData(PlanConfiguration planConfig) {

    }
}
