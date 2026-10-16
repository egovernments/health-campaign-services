package org.egov.id.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DispatchLimitConfig {
    boolean perDayEnabled;
    int totalLimit;
    int perDayLimit;
    int perDayExpireDays;
    int totalExpireDays;
    boolean restrictToTodayEnabled;
}
