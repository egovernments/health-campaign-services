package org.egov.project.util;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NoResourceTaskStatus {

  @Getter
  private final Set<String> statusSet = new HashSet<>();

  @Value("${project.task.no.resource.validation.status}")
  private String statusValues;

  @PostConstruct
  public void initializeConstants() {
    String[] statuses = statusValues.split(",");
    Collections.addAll(statusSet, statuses);
  }

  public boolean isExists(String status) {
    return statusSet.contains(status);
  }
}
