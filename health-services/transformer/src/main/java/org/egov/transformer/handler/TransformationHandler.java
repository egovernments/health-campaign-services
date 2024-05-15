package org.egov.transformer.handler;

import org.egov.transformer.enums.Operation;

import java.util.List;

public interface TransformationHandler<T> {

    void handle(List<T> payloadList, Operation operation);
}
