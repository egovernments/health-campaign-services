package org.egov.common.error.handler;

import org.egov.tracer.ExceptionAdvise;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class ErrorHandler {

    private final ExceptionAdvise exceptionAdvise;

    public static ExceptionAdvise exceptionAdviseInstance;

    @Autowired
    public ErrorHandler(ExceptionAdvise exceptionAdvise) {
        this.exceptionAdvise = exceptionAdvise;
    }

    @PostConstruct
    private void init() {
        exceptionAdviseInstance = exceptionAdvise;
    }


}
