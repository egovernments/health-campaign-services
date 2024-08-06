package org.egov.project.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NoResourceTaskStatus {

    @Getter
    private Set<String> statusSet = new HashSet<>();

    @Value("${project.task.no.resource.validation.status}")
    private String statusValues;

    @PostConstruct
    public void initializeConstants() {
        statusSet = Arrays.stream(statusValues.split(",")).map(s -> s.trim()).collect(Collectors.toSet());
    }

    public boolean isExists(String status) {
        return statusSet.contains(status);
    }
}
