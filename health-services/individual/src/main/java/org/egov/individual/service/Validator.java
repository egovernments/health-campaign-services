package org.egov.individual.service;

import java.util.List;

public interface Validator<T> {
    List<ErrorDetails> validate(T t);
}
