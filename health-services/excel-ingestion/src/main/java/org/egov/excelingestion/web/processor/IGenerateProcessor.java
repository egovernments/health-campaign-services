package org.egov.excelingestion.web.processor;

import org.egov.excelingestion.web.models.GeneratedResource;
import org.egov.excelingestion.web.models.GeneratedResourceRequest;

public interface IGenerateProcessor {
    GeneratedResource process(GeneratedResourceRequest request);
    String getType();
}
