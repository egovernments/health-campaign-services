package org.egov.common.models.idgen;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovModel;

@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class IdTransactionLog extends EgovModel {
    private String userUuid;
    private String deviceUuid;
    private Object deviceInfo;
    private String status;
}

