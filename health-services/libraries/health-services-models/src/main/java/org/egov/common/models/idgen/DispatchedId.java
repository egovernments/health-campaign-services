package org.egov.common.models.idgen;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class DispatchedId {

    private String id;           // will map to id_reference
    private String userUuid;
    private String deviceUuid;
    private Object deviceInfo;
    private String status;// can be Map<String, Object> or raw JSON
}

