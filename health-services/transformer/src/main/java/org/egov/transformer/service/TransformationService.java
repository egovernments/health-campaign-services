package org.egov.transformer.service;

import org.egov.transformer.enums.Operation;

import java.util.List;

public interface TransformationService<T> {

    void transform(List<T> payloadList);

    Operation getOperation();
}
