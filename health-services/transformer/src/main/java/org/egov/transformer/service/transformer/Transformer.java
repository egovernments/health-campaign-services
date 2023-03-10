package org.egov.transformer.service.transformer;

import java.util.List;

public interface Transformer<T, R> {
    List<R> transform(T t);
}