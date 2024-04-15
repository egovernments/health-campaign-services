package org.egov.processor.service;


import org.egov.processor.web.models.Plan;
import org.egov.processor.web.models.PlanConfiguration;

public interface FileParser {

    public default void parseFileData(Plan plan, PlanConfiguration planConfig) {

    }
}
