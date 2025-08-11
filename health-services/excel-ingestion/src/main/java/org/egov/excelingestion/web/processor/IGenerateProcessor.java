package org.egov.excelingestion.web.processor;

import org.egov.excelingestion.web.models.GenerateResourceRequest;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.RequestInfo;

import java.io.IOException;

public interface IGenerateProcessor {

    GenerateResource process(GenerateResourceRequest request);

    byte[] generateExcel(GenerateResource generateResource, RequestInfo requestInfo) throws IOException;

    String getType();
}
