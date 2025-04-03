package org.egov.id.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DispatchUserInfo {
    private String userUuid;
    private int count;
    private String deviceUuid;
    private String deviceInfo;
}
