package org.egov.excelingestion.web.processor;

import org.egov.excelingestion.web.models.GenerateResourceRequest;
import org.egov.excelingestion.web.models.GenerateResource;

public interface IGenerateProcessor {
    GenerateResource process(GenerateResourceRequest request);
    String getType();
}
